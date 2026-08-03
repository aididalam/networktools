package com.nexdevlab.networktools.data

import android.content.Context
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class IpFinderRepository(private val context: Context) {
    private val dnsRepository = DnsLookupRepository(context)

    fun prepare(config: IpFinderConfig): PreparedIpFinderLookup {
        config.validate()
        val normalized = config.normalizedTarget()
        val commonConfig: (String, DnsRecordType) -> DnsQueryConfig = { name, type ->
            DnsQueryConfig(
                name = name,
                recordType = type,
                resolverMode = config.resolverMode,
                customResolver = config.customResolver,
                timeoutMillis = config.timeoutMillis,
            )
        }
        val probe = dnsRepository.prepare(commonConfig(ResolverProbeName, DnsRecordType.TXT), compare = false)
        val domainQueries = if (normalized != null && !normalized.isNumericIpLiteral()) {
            listOf(DnsRecordType.A, DnsRecordType.AAAA).map { type ->
                dnsRepository.prepare(commonConfig(normalized, type), compare = false)
            }
        } else emptyList()
        return PreparedIpFinderLookup(config, normalized, probe, domainQueries)
    }

    suspend fun lookup(prepared: PreparedIpFinderLookup): IpFinderResult = withContext(Dispatchers.IO) {
        require(!prepared.localNetworkResolver || PortScannerRepository.hasLocalNetworkPermission(context = context)) {
            "Nearby devices permission is required to query the local DNS resolver"
        }
        val addresses = when (prepared.config.mode) {
            IpFinderMode.PUBLIC_IP -> listOf(fetchPublicIp())
            IpFinderMode.TARGET -> resolveTarget(prepared)
        }
        val publicAddresses = addresses.distinct().filter { value ->
            runCatching { isPublicAddress(InetAddress.getByName(value)) }.getOrDefault(false)
        }.take(MaximumResolvedAddresses)
        require(publicAddresses.isNotEmpty()) { "The target did not resolve to a public IP address" }
        val selected = publicAddresses.firstOrNull { InetAddress.getByName(it) is Inet4Address }
            ?: publicAddresses.first()
        ensureActive()
        val (registration, origin) = coroutineScope {
            val registrationTask = async { runCatching { fetchRegistration(selected) } }
            val originTask = async { runCatching { queryOriginAsn(selected, prepared) } }
            registrationTask.await() to originTask.await()
        }
        ensureActive()
        val notices = buildList {
            if (registration.isFailure) add("Registration organization is unavailable")
            if (origin.isFailure || origin.getOrNull() == null) add("Origin ASN is unavailable")
        }
        IpFinderResult(
            mode = prepared.config.mode,
            query = prepared.normalizedTarget ?: "Current connection",
            resolvedAddresses = publicAddresses,
            selectedAddress = selected,
            resolver = prepared.resolverProbe.resolvers.single(),
            registration = registration.getOrNull(),
            origin = origin.getOrNull(),
            notices = notices,
            completedAtMillis = System.currentTimeMillis(),
        )
    }

    private suspend fun resolveTarget(prepared: PreparedIpFinderLookup): List<String> {
        val normalized = requireNotNull(prepared.normalizedTarget)
        if (normalized.isNumericIpLiteral()) return listOf(normalized)
        val results = coroutineScope {
            prepared.domainQueries.map { query -> async { dnsRepository.lookup(query) } }.awaitAll()
        }
        val answers = results.flatMap(DnsLookupResult::answerRecords)
            .filter { it.type == DnsRecordType.A.label || it.type == DnsRecordType.AAAA.label }
            .map(DnsRecordEntry::value)
            .distinct()
        if (answers.isEmpty()) {
            val status = results.map(DnsLookupResult::status).distinct().joinToString(" / ") { it.label }
            throw IllegalArgumentException(status.ifEmpty { "No DNS address records were returned" })
        }
        return answers
    }

    private suspend fun fetchPublicIp(): String {
        val response = getText(PublicIpUrl, MaximumPublicIpResponseBytes, "text/plain").trim()
        val normalized = IpHostConversionConfig.normalizeNumericIp(response)
        require(isPublicAddress(InetAddress.getByName(normalized))) {
            "The public-IP endpoint returned a non-public address"
        }
        return normalized
    }

    private suspend fun fetchRegistration(ip: String): IpFinderRegistration {
        val address = InetAddress.getByName(ip)
        val bootstrapUrl = if (address is Inet4Address) IanaIpv4BootstrapUrl else IanaIpv6BootstrapUrl
        val bootstrap = JSONObject(getText(bootstrapUrl, MaximumBootstrapBytes, "application/json"))
        val rdapBase = selectRdapBase(address, bootstrap)
            ?: throw IllegalArgumentException("No authoritative RDAP service was found")
        validateRdapUrl(rdapBase)
        val rdapUrl = rdapBase.trimEnd('/') + "/ip/" + ip
        val response = JSONObject(getText(rdapUrl, MaximumRdapBytes, "application/rdap+json, application/json"))
        return parseRegistration(response, rdapBase)
    }

    private suspend fun queryOriginAsn(ip: String, prepared: PreparedIpFinderLookup): IpFinderAsn? {
        val queryName = originQueryName(InetAddress.getByName(ip))
        val query = dnsRepository.prepare(
            DnsQueryConfig(
                name = queryName,
                recordType = DnsRecordType.TXT,
                resolverMode = prepared.config.resolverMode,
                customResolver = prepared.config.customResolver,
                timeoutMillis = prepared.config.timeoutMillis,
            ),
            compare = false,
        )
        val result = dnsRepository.lookup(query)
        val text = result.answerRecords.firstOrNull { it.type == DnsRecordType.TXT.label }?.value ?: return null
        return parseOriginAsn(text)
    }

    private suspend fun getText(url: String, maxBytes: Int, accept: String): String {
        val parsed = URL(url)
        require(parsed.protocol == "https" && parsed.userInfo == null && parsed.host.isNotBlank()) {
            "Only a validated HTTPS endpoint is allowed"
        }
        val connection = parsed.openConnection() as HttpsURLConnection
        val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause is CancellationException) connection.disconnect()
        }
        return try {
            connection.connectTimeout = ConnectTimeoutMillis
            connection.readTimeout = ReadTimeoutMillis
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("User-Agent", "NetworkTools/1.0")
            val code = connection.responseCode
            when {
                code == 429 -> throw IllegalArgumentException("The lookup service rate limit was reached")
                code !in 200..299 -> throw IllegalArgumentException("Lookup service returned HTTP $code")
            }
            val declaredLength = connection.contentLengthLong
            require(declaredLength == -1L || declaredLength <= maxBytes) { "Lookup response is too large" }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= maxBytes) { "Lookup response is too large" }
                    output.write(buffer, 0, count)
                }
            }
            output.toString(StandardCharsets.UTF_8.name())
        } finally {
            cancellationHandle.dispose()
            connection.disconnect()
        }
    }

    companion object {
        private const val PublicIpUrl = "https://api64.ipify.org"
        private const val IanaIpv4BootstrapUrl = "https://data.iana.org/rdap/ipv4.json"
        private const val IanaIpv6BootstrapUrl = "https://data.iana.org/rdap/ipv6.json"
        private const val ResolverProbeName = "resolver-check.invalid"
        private const val ConnectTimeoutMillis = 4_000
        private const val ReadTimeoutMillis = 5_000
        private const val MaximumPublicIpResponseBytes = 128
        private const val MaximumBootstrapBytes = 768 * 1024
        private const val MaximumRdapBytes = 1024 * 1024
        const val MaximumResolvedAddresses = 16

        internal fun isPublicAddress(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress) return false
            val bytes = address.address
            if (address is Inet4Address) {
                val first = bytes[0].toInt() and 0xff
                val second = bytes[1].toInt() and 0xff
                val third = bytes[2].toInt() and 0xff
                if (first == 0 || first >= 224) return false
                if (first == 100 && second in 64..127) return false
                if (first == 192 && second == 0) return false
                if (first == 192 && second == 88 && third == 99) return false
                if (first == 198 && (second in 18..19 || (second == 51 && third == 100))) return false
                if (first == 203 && second == 0 && third == 113) return false
                return true
            }
            if (address is Inet6Address) {
                val first = bytes[0].toInt() and 0xff
                val second = bytes[1].toInt() and 0xff
                if (first !in 0x20..0x3f) return false
                if (first == 0x20 && second == 0x01 && (bytes[2].toInt() and 0xff) == 0x0d &&
                    (bytes[3].toInt() and 0xff) == 0xb8) return false
                return true
            }
            return false
        }

        internal fun selectRdapBase(address: InetAddress, bootstrap: JSONObject): String? {
            val services = bootstrap.optJSONArray("services") ?: return null
            for (index in 0 until services.length()) {
                val service = services.optJSONArray(index) ?: continue
                val prefixes = service.optJSONArray(0) ?: continue
                for (prefixIndex in 0 until prefixes.length()) {
                    if (addressInPrefix(address, prefixes.optString(prefixIndex))) {
                        return service.optJSONArray(1)?.optString(0)?.takeIf { it.startsWith("https://") }
                    }
                }
            }
            return null
        }

        internal fun parseRegistration(response: JSONObject, registryUrl: String): IpFinderRegistration =
            IpFinderRegistration(
                organization = findOrganization(response.optJSONArray("entities"), 0),
                networkName = response.optString("name").takeIf(String::isNotBlank),
                registryUrl = registryUrl,
            )

        internal fun parseOriginAsn(raw: String): IpFinderAsn? {
            val clean = raw.trim().removeSurrounding("\"")
            val fields = clean.split('|').map(String::trim)
            if (fields.isEmpty()) return null
            val numbers = fields[0].split(Regex("\\s+"))
                .filter { it.isNotBlank() && it.all(Char::isDigit) }
                .distinct()
                .take(4)
                .map { "AS$it" }
            if (numbers.isEmpty()) return null
            return IpFinderAsn(
                numbers = numbers,
                routedPrefix = fields.getOrNull(1)?.takeIf(String::isNotBlank),
                registry = fields.getOrNull(3)?.lowercase(Locale.US)?.takeIf(String::isNotBlank),
            )
        }

        internal fun originQueryName(address: InetAddress): String = when (address) {
            is Inet4Address -> address.hostAddress.orEmpty().split('.').reversed().joinToString(".") +
                ".origin.asn.cymru.com"
            is Inet6Address -> address.address.joinToString("") { "%02x".format(it.toInt() and 0xff) }
                .reversed().toCharArray().joinToString(".") + ".origin6.asn.cymru.com"
            else -> throw IllegalArgumentException("Unsupported IP address family")
        }

        private fun validateRdapUrl(value: String) {
            val url = URL(value)
            require(url.protocol == "https" && url.userInfo == null && url.host.isNotBlank() &&
                (url.port == -1 || url.port == 443) && url.query == null && url.ref == null) {
                "The RDAP bootstrap returned an unsafe service URL"
            }
            val addresses = InetAddress.getAllByName(url.host)
            require(addresses.isNotEmpty() && addresses.all(::isPublicAddress)) {
                "The RDAP service does not resolve to a public address"
            }
        }

        private fun findOrganization(entities: JSONArray?, depth: Int): String? {
            if (entities == null || depth > 2) return null
            for (preferredOnly in listOf(true, false)) {
                for (index in 0 until entities.length()) {
                    val entity = entities.optJSONObject(index) ?: continue
                    val roles = entity.optJSONArray("roles") ?: JSONArray()
                    val preferred = (0 until roles.length()).any {
                        roles.optString(it) in setOf("registrant", "administrative", "technical")
                    }
                    if (!preferredOnly || preferred) {
                        vCardName(entity.optJSONArray("vcardArray"))?.let { return it }
                    }
                    findOrganization(entity.optJSONArray("entities"), depth + 1)?.let { return it }
                }
            }
            return null
        }

        private fun vCardName(vcard: JSONArray?): String? {
            val properties = vcard?.optJSONArray(1) ?: return null
            for (wanted in listOf("org", "fn")) {
                for (index in 0 until properties.length()) {
                    val property = properties.optJSONArray(index) ?: continue
                    if (property.optString(0) != wanted) continue
                    val value = property.opt(3)
                    val text = when (value) {
                        is String -> value
                        is JSONArray -> (0 until value.length()).map(value::optString)
                            .filter(String::isNotBlank).joinToString(" ")
                        else -> null
                    }
                    text?.takeIf(String::isNotBlank)?.let { return it }
                }
            }
            return null
        }

        private fun addressInPrefix(address: InetAddress, prefix: String): Boolean {
            val parts = prefix.split('/', limit = 2)
            if (parts.size != 2) return false
            val network = runCatching { InetAddress.getByName(parts[0]) }.getOrNull() ?: return false
            val bits = parts[1].toIntOrNull() ?: return false
            if (address.address.size != network.address.size || bits !in 0..address.address.size * 8) return false
            val wholeBytes = bits / 8
            val remainingBits = bits % 8
            for (index in 0 until wholeBytes) if (address.address[index] != network.address[index]) return false
            if (remainingBits == 0) return true
            val mask = (0xff shl (8 - remainingBits)) and 0xff
            return (address.address[wholeBytes].toInt() and mask) ==
                (network.address[wholeBytes].toInt() and mask)
        }
    }
}
