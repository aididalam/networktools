package com.nexdevlab.networktools.data

import java.io.ByteArrayOutputStream
import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

class WhoisRepository {
    suspend fun lookup(
        input: String,
        queryType: WhoisQueryType,
        serverMode: WhoisServerMode,
        manualServer: String?,
    ): WhoisResult = withContext(Dispatchers.IO) {
        val query = when (queryType) {
            WhoisQueryType.DOMAIN -> normalizeDomain(input)
            WhoisQueryType.IP_ADDRESS -> normalizeIpAddress(input)
        }
        val responses = mutableListOf<ServerResponse>()

        if (serverMode == WhoisServerMode.MANUAL) {
            val server = normalizeWhoisServer(manualServer.orEmpty())
            responses += ServerResponse(server, queryWhois(server, query))
        } else {
            val selectionQuery = if (queryType == WhoisQueryType.DOMAIN) {
                query.substringAfterLast('.')
            } else {
                query
            }
            val ianaResponse = queryWhois(IanaWhoisServer, selectionQuery)
            val authoritativeServer = referralServer(ianaResponse)
                ?: throw WhoisLookupException("No authoritative WHOIS server was found")
            responses += ServerResponse(authoritativeServer, queryWhois(authoritativeServer, query))
            coroutineContext.ensureActive()

            if (queryType == WhoisQueryType.DOMAIN) {
                val registrarServer = registrarWhoisServer(responses.last().body)
                if (registrarServer != null && registrarServer != authoritativeServer) {
                    runCatching { queryWhois(registrarServer, query) }
                        .onSuccess { responses += ServerResponse(registrarServer, it) }
                }
            }
        }

        parseWhois(query, queryType, responses)
    }

    private suspend fun queryWhois(server: String, query: String): String {
        val normalizedServer = normalizeWhoisServer(server)
        val addresses = InetAddress.getAllByName(normalizedServer)
            .filter(::isPublicAddress)
        if (addresses.isEmpty()) {
            throw WhoisLookupException("The WHOIS server does not resolve to a public address")
        }

        var lastFailure: Exception? = null
        for (address in addresses.take(MaxResolvedAddresses)) {
            coroutineContext.ensureActive()
            try {
                Socket().use { socket ->
                    val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
                        if (cause is CancellationException) runCatching(socket::close)
                    }
                    try {
                        socket.connect(InetSocketAddress(address, WhoisPort), ConnectTimeoutMillis)
                        socket.soTimeout = ReadTimeoutMillis
                        val output = socket.getOutputStream()
                        output.write("$query\r\n".toByteArray(StandardCharsets.US_ASCII))
                        output.flush()
                        val response = ByteArrayOutputStream()
                        socket.getInputStream().use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (response.size() + count > MaxResponseBytes) {
                                    throw WhoisLookupException("The WHOIS response exceeded the size limit")
                                }
                                response.write(buffer, 0, count)
                            }
                        }
                        return response.toString(StandardCharsets.UTF_8.name())
                            .replace("\u0000", "")
                            .trim()
                            .takeIf(String::isNotBlank)
                            ?: throw WhoisLookupException("The WHOIS server returned an empty response")
                    } finally {
                        cancellationHandle.dispose()
                    }
                }
            } catch (failure: WhoisLookupException) {
                throw failure
            } catch (failure: Exception) {
                coroutineContext.ensureActive()
                lastFailure = failure
            }
        }
        throw WhoisLookupException("The WHOIS server could not be reached", lastFailure)
    }

    internal data class ServerResponse(val server: String, val body: String)

    class WhoisLookupException(message: String, cause: Throwable? = null) : Exception(message, cause)

    companion object {
        private const val IanaWhoisServer = "whois.iana.org"
        private const val WhoisPort = 43
        private const val ConnectTimeoutMillis = 5_000
        private const val ReadTimeoutMillis = 7_000
        private const val MaxResponseBytes = 512 * 1024
        private const val MaxResolvedAddresses = 3

        internal fun normalizeDomain(input: String): String {
            val trimmed = input.trim().trimEnd('.')
            require(trimmed.isNotEmpty()) { "Enter a domain name" }
            require(trimmed.length <= 253 && !trimmed.contains(Regex("[\\s/:?#@*]"))) {
                "Enter a valid domain name"
            }
            val ascii = runCatching { IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES) }
                .getOrElse { throw IllegalArgumentException("Enter a valid domain name") }
                .lowercase(Locale.US)
            val labels = ascii.split('.')
            require(labels.size >= 2 && labels.all(::isValidDomainLabel)) {
                "Enter a valid domain name"
            }
            require(!looksLikeIpv4(ascii)) { "Select IP address mode for an IP query" }
            return ascii
        }

        internal fun normalizeIpAddress(input: String): String {
            val trimmed = input.trim()
            require(isNumericIp(trimmed)) { "Enter a valid IPv4 or IPv6 address" }
            return InetAddress.getByName(trimmed).hostAddress.orEmpty().substringBefore('%')
        }

        internal fun normalizeWhoisServer(input: String): String {
            val trimmed = input.trim().trimEnd('.').lowercase(Locale.US)
            require(trimmed.isNotEmpty()) { "Enter a WHOIS server" }
            require(trimmed.length <= 253 && !trimmed.contains(Regex("[\\s/:?#@*]"))) {
                "Enter a WHOIS server hostname without a port or URL"
            }
            if (isNumericIp(trimmed)) return normalizeIpAddress(trimmed)
            val ascii = runCatching { IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES) }
                .getOrElse { throw IllegalArgumentException("Enter a valid WHOIS server") }
            require(ascii.split('.').size >= 2 && ascii.split('.').all(::isValidDomainLabel)) {
                "Enter a valid WHOIS server"
            }
            return ascii.lowercase(Locale.US)
        }

        internal fun parseWhois(
            query: String,
            queryType: WhoisQueryType,
            responses: List<ServerResponse>,
        ): WhoisResult {
            require(responses.isNotEmpty())
            val responseBodies = responses.asReversed().map(ServerResponse::body)
            fun firstValue(vararg keys: String): String? {
                val wanted = keys.map(::normalizeKey).toSet()
                responseBodies.forEach { body ->
                    body.lineSequence().forEach { line ->
                        val pair = keyValue(line) ?: return@forEach
                        if (normalizeKey(pair.first) in wanted && pair.second.isNotBlank()) {
                            return pair.second
                        }
                    }
                }
                return null
            }
            val nameServerKeys = setOf("nameserver", "nserver")
            val nameServers = responseBodies.asSequence()
                .flatMap(String::lineSequence)
                .mapNotNull(::keyValue)
                .filter { normalizeKey(it.first) in nameServerKeys }
                .map { it.second.substringBefore(' ').trim().trimEnd('.').lowercase(Locale.US) }
                .filter { it.isNotBlank() }
                .distinct()
                .take(20)
                .toList()
            val raw = responses.joinToString("\n\n") {
                "----- ${it.server} -----\n${it.body}"
            }

            return WhoisResult(
                query = query,
                queryType = queryType,
                servers = responses.map(ServerResponse::server),
                registrar = firstValue("Registrar", "Registrar Name", "Sponsoring Registrar"),
                registrationDate = firstValue(
                    "Creation Date",
                    "Created On",
                    "Created",
                    "Registered On",
                    "Registration Time",
                ),
                expirationDate = firstValue(
                    "Registry Expiry Date",
                    "Registrar Registration Expiration Date",
                    "Expiration Date",
                    "Expiry Date",
                    "Paid-till",
                ),
                nameServers = nameServers,
                registrant = firstValue("Registrant Name", "Holder", "Person"),
                organization = firstValue(
                    "Registrant Organization",
                    "Organization",
                    "Org-name",
                    "OrgName",
                ),
                ipBlockOwner = if (queryType == WhoisQueryType.IP_ADDRESS) {
                    firstValue("OrgName", "Owner", "NetName", "Descr", "Organization")
                } else {
                    null
                },
                autonomousSystem = firstValue("OriginAS", "Origin", "Aut-num"),
                rawResponse = raw,
            )
        }

        private fun referralServer(response: String): String? {
            val value = valueFor(response, "refer", "whois") ?: return null
            return runCatching { normalizeWhoisServer(value.substringBefore(' ')) }.getOrNull()
        }

        private fun registrarWhoisServer(response: String): String? {
            val value = valueFor(response, "Registrar WHOIS Server", "WHOIS Server") ?: return null
            return runCatching { normalizeWhoisServer(value.substringBefore(' ')) }.getOrNull()
        }

        private fun valueFor(response: String, vararg keys: String): String? {
            val wanted = keys.map(::normalizeKey).toSet()
            return response.lineSequence()
                .mapNotNull(::keyValue)
                .firstOrNull { normalizeKey(it.first) in wanted }
                ?.second
                ?.takeIf(String::isNotBlank)
        }

        private fun keyValue(line: String): Pair<String, String>? {
            val clean = line.trim()
            if (clean.isEmpty() || clean.startsWith('%') || clean.startsWith('#')) return null
            val separator = clean.indexOf(':')
            if (separator <= 0) return null
            return clean.substring(0, separator).trim() to clean.substring(separator + 1).trim()
        }

        private fun normalizeKey(value: String): String =
            value.lowercase(Locale.US).filter(Char::isLetterOrDigit)

        private fun isValidDomainLabel(label: String): Boolean =
            label.isNotEmpty() && label.length <= 63 &&
                label.first().isLetterOrDigit() && label.last().isLetterOrDigit() &&
                label.all { it.isLetterOrDigit() || it == '-' }

        private fun looksLikeIpv4(value: String): Boolean =
            value.split('.').let { parts ->
                parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }
            }

        private fun isNumericIp(value: String): Boolean {
            if (value.isBlank() || value.length > 45 || value.any(Char::isWhitespace)) return false
            if (':' in value) {
                return value.count { it == ':' } >= 2 && value.all {
                    it.isDigit() || it.lowercaseChar() in 'a'..'f' || it in ":."
                }
            }
            return looksLikeIpv4(value)
        }

        private fun isPublicAddress(address: InetAddress): Boolean {
            if (
                address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress
            ) return false
            val bytes = address.address
            if (address is Inet4Address) {
                val first = bytes[0].toInt() and 0xff
                val second = bytes[1].toInt() and 0xff
                val third = bytes[2].toInt() and 0xff
                if (first == 0 || first >= 224) return false
                if (first == 100 && second in 64..127) return false
                if (first == 192 && second == 0 && third in setOf(0, 2)) return false
                if (first == 198 && (second == 18 || second == 19 || (second == 51 && third == 100))) return false
                if (first == 203 && second == 0 && third == 113) return false
            }
            if (address is Inet6Address && (bytes[0].toInt() and 0xfe) == 0xfc) return false
            return true
        }
    }
}
