package com.nexdevlab.networktools.data

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.ARecord
import org.xbill.DNS.CAARecord
import org.xbill.DNS.CNAMERecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.MXRecord
import org.xbill.DNS.Message
import org.xbill.DNS.NSRecord
import org.xbill.DNS.Name
import org.xbill.DNS.PTRRecord
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.ResolverConfig
import org.xbill.DNS.SOARecord
import org.xbill.DNS.SRVRecord
import org.xbill.DNS.Section
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.TXTRecord
import org.xbill.DNS.Type
import org.xbill.DNS.WireParseException

class DnsLookupRepository(private val context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun prepare(config: DnsQueryConfig, compare: Boolean): PreparedDnsQuery {
        config.validate()
        val name = DnsQueryConfig.normalizeName(config.name, config.recordType)
        val resolvers = if (compare) comparisonResolvers() else listOf(selectedResolver(config))
        require(resolvers.isNotEmpty()) { "No DNS resolver is available on the active network" }
        val privateDns = privateDnsStatus()
        return PreparedDnsQuery(
            config = config,
            normalizedName = name,
            resolvers = resolvers.take(MaximumComparisonResolvers),
            privateDnsActive = privateDns.first,
            privateDnsServerName = privateDns.second,
            localNetworkResolver = resolvers.any { target ->
                runCatching {
                    PortScannerRepository.isLocalNetworkAddress(DnsQueryConfig.parseResolverAddress(target.address))
                }.getOrDefault(false)
            },
        )
    }

    suspend fun lookup(prepared: PreparedDnsQuery): DnsLookupResult =
        query(prepared, prepared.resolvers.single())

    suspend fun compare(
        prepared: PreparedDnsQuery,
        onResult: (DnsLookupResult) -> Unit,
    ): DnsComparisonResult = coroutineScope {
        val results = prepared.resolvers.map { resolver ->
            async {
                val result = try {
                    query(prepared, resolver)
                } catch (failure: Exception) {
                    ensureActive()
                    failureResult(prepared, resolver, failure)
                }
                onResult(result)
                result
            }
        }.awaitAll().sortedBy { result -> prepared.resolvers.indexOf(result.resolver) }
        DnsComparisonResult(
            queryName = prepared.normalizedName,
            recordType = prepared.config.recordType,
            results = results,
            completedAtMillis = System.currentTimeMillis(),
        )
    }

    fun systemResolvers(): List<DnsResolverTarget> {
        val active = connectivityManager.activeNetwork
        val addresses = active?.let(connectivityManager::getLinkProperties)?.dnsServers.orEmpty()
            .ifEmpty {
                runCatching { ResolverConfig.getCurrentConfig().servers().map(InetSocketAddress::getAddress) }
                    .getOrDefault(emptyList())
            }
            .filterNot { it.isAnyLocalAddress || it.isMulticastAddress }
            .distinctBy { it.hostAddress }
        return addresses.mapIndexed { index, address ->
            DnsResolverTarget(
                label = if (addresses.size == 1) "System DNS" else "System DNS ${index + 1}",
                address = address.hostAddress.orEmpty(),
                system = true,
            )
        }
    }

    private suspend fun query(prepared: PreparedDnsQuery, target: DnsResolverTarget): DnsLookupResult =
        withContext(Dispatchers.IO) {
            require(!prepared.localNetworkResolver || PortScannerRepository.hasLocalNetworkPermission(context)) {
                "Nearby devices permission is required to query the local DNS resolver"
            }
            val address = DnsQueryConfig.parseResolverAddress(target.address)
            val questionName = Name.fromString("${prepared.normalizedName}.")
            val question = Record.newRecord(questionName, prepared.config.recordType.code, DClass.IN)
            val request = Message.newQuery(question)
            val started = System.nanoTime()
            try {
                val udpResolver = resolver(address.hostAddress.orEmpty(), prepared.config.timeoutMillis, tcp = false)
                val udpResponse = udpResolver.sendCancellable(request)
                val truncated = udpResponse.header.getFlag(Flags.TC.toInt())
                val response = if (truncated) {
                    resolver(address.hostAddress.orEmpty(), prepared.config.timeoutMillis, tcp = true)
                        .sendCancellable(request)
                } else udpResponse
                parseResponse(
                    response = response,
                    prepared = prepared,
                    target = target,
                    latencyMillis = elapsedMillis(started),
                    usedTcp = truncated,
                )
            } catch (failure: Exception) {
                ensureActive()
                failureResult(prepared, target, failure, elapsedMillis(started))
            }
        }

    private fun selectedResolver(config: DnsQueryConfig): DnsResolverTarget = when (config.resolverMode) {
        DnsResolverMode.SYSTEM -> systemResolvers().firstOrNull()
            ?: throw IllegalArgumentException("No system DNS resolver is available")
        DnsResolverMode.CUSTOM -> {
            val address = DnsQueryConfig.parseResolverAddress(config.customResolver).hostAddress.orEmpty()
            PublicResolvers.firstOrNull { it.address == address }
                ?: DnsResolverTarget("Custom DNS", address)
        }
    }

    private fun comparisonResolvers(): List<DnsResolverTarget> =
        (systemResolvers().take(1) + PublicResolvers).distinctBy(DnsResolverTarget::address)

    private fun privateDnsStatus(): Pair<Boolean, String?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false to null
        val properties = connectivityManager.activeNetwork?.let(connectivityManager::getLinkProperties)
        return (properties?.isPrivateDnsActive == true) to properties?.privateDnsServerName
    }

    private fun resolver(address: String, timeoutMillis: Int, tcp: Boolean): SimpleResolver =
        SimpleResolver(InetSocketAddress(DnsQueryConfig.parseResolverAddress(address), DnsPort)).apply {
            setTimeout(Duration.ofMillis(timeoutMillis.toLong()))
            setTCP(tcp)
            setIgnoreTruncation(true)
            setEDNS(0, MaximumUdpPayload, 0, emptyList())
        }

    private suspend fun SimpleResolver.sendCancellable(message: Message): Message =
        suspendCancellableCoroutine { continuation ->
            val future = sendAsync(message).toCompletableFuture()
            continuation.invokeOnCancellation { future.cancel(true) }
            future.whenComplete { response, failure ->
                if (failure == null) {
                    if (continuation.isActive) continuation.resume(response)
                } else if (continuation.isActive) {
                    continuation.resumeWithException(unwrap(failure))
                }
            }
        }

    private fun failureResult(
        prepared: PreparedDnsQuery,
        target: DnsResolverTarget,
        failure: Exception,
        latencyMillis: Double = 0.0,
    ): DnsLookupResult {
        val actual = unwrap(failure)
        val status = when (actual) {
            is SocketTimeoutException, is java.util.concurrent.TimeoutException -> DnsResponseStatus.TIMED_OUT
            is WireParseException -> DnsResponseStatus.MALFORMED
            is SSLException, is IOException -> DnsResponseStatus.NETWORK_ERROR
            else -> DnsResponseStatus.NETWORK_ERROR
        }
        return DnsLookupResult(
            queryName = prepared.normalizedName,
            recordType = prepared.config.recordType,
            resolver = target,
            status = status,
            responseCode = status.label,
            records = emptyList(),
            latencyMillis = latencyMillis,
            usedTcp = false,
            privateDnsActive = prepared.privateDnsActive,
            privateDnsServerName = prepared.privateDnsServerName,
            completedAtMillis = System.currentTimeMillis(),
            detail = when (status) {
                DnsResponseStatus.TIMED_OUT -> "The resolver did not answer within the selected timeout."
                DnsResponseStatus.MALFORMED -> "The resolver returned a malformed DNS message."
                else -> "The DNS query could not reach this resolver."
            },
        )
    }

    companion object {
        const val DnsPort = 53
        const val MaximumUdpPayload = 1_232
        const val MaximumResponseRecords = 100
        const val MaximumComparisonResolvers = 4

        val PublicResolvers = listOf(
            DnsResolverTarget("Cloudflare", "1.1.1.1"),
            DnsResolverTarget("Google", "8.8.8.8"),
            DnsResolverTarget("Quad9", "9.9.9.9"),
        )

        internal fun parseResponse(
            response: Message,
            prepared: PreparedDnsQuery,
            target: DnsResolverTarget,
            latencyMillis: Double,
            usedTcp: Boolean,
        ): DnsLookupResult {
            val records = buildList {
                addSection(response, Section.ANSWER, DnsSection.ANSWER)
                addSection(response, Section.AUTHORITY, DnsSection.AUTHORITY)
                addSection(response, Section.ADDITIONAL, DnsSection.ADDITIONAL)
            }.take(MaximumResponseRecords)
            val rcode = response.rcode
            val answerCount = records.count { it.section == DnsSection.ANSWER }
            val status = when (rcode) {
                Rcode.NOERROR -> if (answerCount > 0) DnsResponseStatus.SUCCESS else DnsResponseStatus.NO_RECORDS
                Rcode.NXDOMAIN -> DnsResponseStatus.NXDOMAIN
                Rcode.REFUSED -> DnsResponseStatus.REFUSED
                Rcode.SERVFAIL -> DnsResponseStatus.SERVER_FAILURE
                else -> DnsResponseStatus.SERVER_FAILURE
            }
            return DnsLookupResult(
                queryName = prepared.normalizedName,
                recordType = prepared.config.recordType,
                resolver = target,
                status = status,
                responseCode = Rcode.string(rcode),
                records = records,
                latencyMillis = latencyMillis,
                usedTcp = usedTcp,
                privateDnsActive = prepared.privateDnsActive,
                privateDnsServerName = prepared.privateDnsServerName,
                completedAtMillis = System.currentTimeMillis(),
                detail = when (status) {
                    DnsResponseStatus.NO_RECORDS -> "The name exists, but this response contains no answer records."
                    DnsResponseStatus.NXDOMAIN -> "The resolver reports that this DNS name does not exist."
                    DnsResponseStatus.REFUSED -> "The resolver refused this query."
                    DnsResponseStatus.SERVER_FAILURE -> "The resolver could not complete this query."
                    else -> null
                },
            )
        }

        private fun MutableList<DnsRecordEntry>.addSection(
            response: Message,
            wireSection: Int,
            section: DnsSection,
        ) {
            response.getSection(wireSection)
                .asSequence()
                .filter { it.type != Type.OPT }
                .take(MaximumResponseRecords - size)
                .forEach { record ->
                    add(
                        DnsRecordEntry(
                            section = section,
                            name = record.name.toString().removeSuffix("."),
                            type = Type.string(record.type),
                            value = record.displayValue(),
                            ttlSeconds = record.ttl,
                        ),
                    )
                }
        }

        internal fun Record.displayValue(): String = when (this) {
            is ARecord -> address.hostAddress.orEmpty()
            is AAAARecord -> address.hostAddress.orEmpty()
            is MXRecord -> "$priority ${target.displayName()}"
            is NSRecord -> target.displayName()
            is TXTRecord -> strings.joinToString(" ") { value -> "\"$value\"" }
            is CNAMERecord -> target.displayName()
            is SOARecord -> "${host.displayName()} ${admin.displayName()} " +
                "$serial $refresh $retry $expire $minimum"
            is PTRRecord -> target.displayName()
            is SRVRecord -> "$priority $weight $port ${target.displayName()}"
            is CAARecord -> "$flags $tag \"$value\""
            else -> rdataToString()
        }

        private fun Name.displayName(): String = toString().let { if (it == ".") it else it.removeSuffix(".") }

        private fun unwrap(failure: Throwable): Exception {
            var current = failure
            while ((current is CompletionException || current is ExecutionException) && current.cause != null) {
                current = current.cause!!
            }
            return current as? Exception ?: IOException(current.message, current)
        }

        private fun elapsedMillis(started: Long): Double = (System.nanoTime() - started) / 1_000_000.0
    }
}
