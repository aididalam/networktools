package com.nexdevlab.networktools

import com.nexdevlab.networktools.data.SpeedTestResult
import com.nexdevlab.networktools.data.SpeedServerChoice
import com.nexdevlab.networktools.data.SpeedTestServer
import com.nexdevlab.networktools.data.SpeedEndpointChoice
import com.nexdevlab.networktools.data.calculateJitter
import com.nexdevlab.networktools.data.calculateMegabitsPerSecond
import com.nexdevlab.networktools.data.parseEdgeHeaders
import com.nexdevlab.networktools.data.parseServerTimeMillis
import com.nexdevlab.networktools.data.percentile
import com.nexdevlab.networktools.data.rankSpeedServers
import com.nexdevlab.networktools.data.speedEndpointChoices
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedTestModelsTest {
    @Test fun throughputUsesDecimalMegabitsPerSecond() {
        assertEquals(8.0, calculateMegabitsPerSecond(1_000_000, 1_000_000), 0.0001)
        assertEquals(0.0, calculateMegabitsPerSecond(100, 0), 0.0)
        assertEquals(0.0, calculateMegabitsPerSecond(-1, 1), 0.0)
    }

    @Test fun jitterIsAverageAbsoluteDifferenceAndRejectsInsufficientSamples() {
        assertEquals(3.0, requireNotNull(calculateJitter(listOf(10.0, 14.0, 12.0))), 0.0001)
        assertNull(calculateJitter(listOf(10.0)))
        assertNull(calculateJitter(listOf(Double.NaN, -1.0)))
    }

    @Test fun serverChoicesPutNearestEdgeBeforeEveryIsoCountry() {
        val choices = SpeedServerChoice.all(Locale.US)
        assertEquals(SpeedServerChoice.LOCAL, choices.first())
        assertEquals(SpeedServerChoice.AUTOMATIC, choices[1])
        assertEquals(Locale.getISOCountries().size + 2, choices.size)
        assertEquals(Locale.getISOCountries().size, choices.drop(2).mapNotNull { it.countryCode }.distinct().size)
        assertEquals("Singapore", choices.single { it.countryCode == "SG" }.label)
        assertEquals("Bangladesh", choices.single { it.countryCode == "BD" }.label)
        assertEquals("Sri Lanka", choices.single { it.countryCode == "LK" }.label)
    }

    @Test fun nearestEdgeMetadataRecognizesBangladeshAndDhaka() {
        val edge = parseEdgeHeaders(mapOf("colo" to listOf("DAC"), "country" to listOf("BD")))
        assertEquals("DAC", edge.edgeCode)
        assertEquals("Dhaka edge", edge.edgeName)
        assertEquals("BD", edge.countryCode)
    }

    @Test fun edgeMetadataFallsBackToRayAndOfficialTimingCalculation() {
        val edge = parseEdgeHeaders(mapOf("CF-Ray" to listOf("abc123-CGP"), "cf-meta-country" to listOf("bd")))
        assertEquals("Chittagong edge", edge.edgeName)
        assertEquals(7.0, parseServerTimeMillis("cfSpeedEdge;dur=2, cfSpeedWorker;dur=5"), 0.001)
        assertEquals(18.0, percentile(listOf(10.0, 20.0), 0.8), 0.001)
    }

    @Test fun automaticRankingUsesLowestMeasuredLatencyAndKeepsFailuresLast() {
        val slow = server("slow")
        val unavailable = server("unavailable")
        val fast = server("fast")
        val ranked = rankSpeedServers(
            listOf(slow, unavailable, fast),
            mapOf(slow to 80L, unavailable to null, fast to 12L),
        )
        assertEquals(listOf(fast, slow, unavailable), ranked)
    }

    @Test fun endpointChoicesDefaultToAutomaticAndHideProviderHostnames() {
        val options = speedEndpointChoices(listOf(server("first"), server("second"), server("first")))
        assertEquals(3, options.size)
        assertEquals(SpeedEndpointChoice.AUTOMATIC, options.first())
        assertEquals("first · Server 1", options[1].label)
        assertFalse(options.joinToString { it.label }.contains("measurement-lab.org"))
    }

    @Test fun copiedResultContainsNoMachineOrSignedServerUrl() {
        val text = SpeedTestResult(
            downloadMbps = 100.2,
            uploadMbps = 40.3,
            latencyMillis = 12.0,
            jitterMillis = 2.0,
            serverCity = "Dhaka",
            serverCountryCode = "BD",
            serverMachine = "token-bearing-host.measurement-lab.org",
            networkType = "Wi-Fi",
            completedAtMillis = 1,
        ).asPlainText()
        assertFalse(text.contains("token-bearing-host"))
        assertFalse(text.contains("access_token"))
        assertFalse(text.contains("wss://"))
    }

    private fun server(name: String) = SpeedTestServer(
        machine = "$name.measurement-lab.org",
        city = name,
        countryCode = "US",
        endpointHost = "$name.measurement-lab.org",
        downloadUrl = "wss://$name.measurement-lab.org/ndt/v7/download?access_token=test",
        uploadUrl = "wss://$name.measurement-lab.org/ndt/v7/upload?access_token=test",
    )
}
