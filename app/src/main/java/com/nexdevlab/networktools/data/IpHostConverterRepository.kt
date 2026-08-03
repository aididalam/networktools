package com.nexdevlab.networktools.data

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class IpHostConverterRepository(context: Context) {
    private val dnsRepository = DnsLookupRepository(context)

    fun prepare(config: IpHostConversionConfig): PreparedIpHostConversion {
        config.validate()
        val normalized = config.normalizedTarget()
        val queries = config.recordTypes().map { type ->
            dnsRepository.prepare(
                DnsQueryConfig(
                    name = normalized,
                    recordType = type,
                    resolverMode = config.resolverMode,
                    customResolver = config.customResolver,
                    timeoutMillis = config.timeoutMillis,
                ),
                compare = false,
            )
        }
        require(queries.size in 1..MaximumQueries) { "A conversion must use one or two bounded DNS queries" }
        return PreparedIpHostConversion(config, normalized, queries)
    }

    suspend fun convert(prepared: PreparedIpHostConversion): IpHostConversionResult = coroutineScope {
        val results = prepared.queries.map { query -> async { dnsRepository.lookup(query) } }.awaitAll()
        IpHostConversionResult.from(prepared, results)
    }

    companion object {
        const val MaximumQueries = 2
    }
}
