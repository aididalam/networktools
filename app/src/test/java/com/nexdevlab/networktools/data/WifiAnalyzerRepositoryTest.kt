package com.nexdevlab.networktools.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiAnalyzerRepositoryTest {
    @Test
    fun frequencyConversion_handlesSupportedBandsAndSpecialChannels() {
        val cases = mapOf(
            2_412 to (WifiBand.BAND_2_4_GHZ to 1),
            2_437 to (WifiBand.BAND_2_4_GHZ to 6),
            2_484 to (WifiBand.BAND_2_4_GHZ to 14),
            5_180 to (WifiBand.BAND_5_GHZ to 36),
            5_745 to (WifiBand.BAND_5_GHZ to 149),
            5_935 to (WifiBand.BAND_6_GHZ to 2),
            5_955 to (WifiBand.BAND_6_GHZ to 1),
            58_320 to (WifiBand.BAND_60_GHZ to 1),
        )

        cases.forEach { (frequency, expected) ->
            assertEquals(expected.first, WifiAnalyzerRepository.bandForFrequency(frequency))
            assertEquals(expected.second, WifiAnalyzerRepository.channelForFrequency(frequency))
        }
    }

    @Test
    fun frequencyConversion_rejectsUnknownAndOffGridValues() {
        listOf(0, 2_410, 2_483, 5_181, 7_200, 60_000).forEach { frequency ->
            assertNull(WifiAnalyzerRepository.channelForFrequency(frequency))
        }
        assertEquals(WifiBand.UNKNOWN, WifiAnalyzerRepository.bandForFrequency(7_200))
    }

    @Test
    fun securityClassification_coversModernLegacyAndOpenNetworks() {
        val cases = mapOf(
            "[RSN-SAE-CCMP][ESS]" to "WPA3 Personal",
            "[RSN-PSK+SAE-CCMP][ESS]" to "WPA2/WPA3 Personal",
            "[RSN-SUITE_B_192-EAP-CCMP][ESS]" to "WPA3 Enterprise",
            "[RSN-EAP-CCMP][ESS]" to "WPA2 Enterprise",
            "[RSN-PSK-CCMP][ESS]" to "WPA2 Personal",
            "[WPA-PSK-TKIP][ESS]" to "WPA Personal",
            "[WEP][ESS]" to "WEP",
            "[OWE][ESS]" to "Enhanced Open",
            "[OWE_TRANSITION][ESS]" to "Open/Enhanced Open",
            "[ESS]" to "Open",
        )

        cases.forEach { (capabilities, expected) ->
            assertEquals(expected, WifiAnalyzerRepository.securityType(capabilities))
        }
    }

    @Test
    fun normalizeObservation_cleansSsidAndMarksHiddenNetworks() {
        val visible = WifiAnalyzerRepository.normalizeObservation(
            observation(ssid = "  Office\u0000 Wi-Fi  "),
        )
        val hidden = WifiAnalyzerRepository.normalizeObservation(observation(ssid = " \t "))

        assertEquals("Office Wi-Fi", visible?.ssid)
        assertFalse(visible?.hidden ?: true)
        assertEquals("Hidden network", hidden?.ssid)
        assertTrue(hidden?.hidden == true)
    }

    @Test
    fun normalizeObservation_rejectsMalformedOrUnsafeValues() {
        val invalid = listOf(
            observation(bssid = null),
            observation(bssid = "not-a-bssid"),
            observation(levelDbm = 1),
            observation(levelDbm = -128),
            observation(frequencyMhz = 1234),
        )

        invalid.forEach { assertNull(WifiAnalyzerRepository.normalizeObservation(it)) }
    }

    @Test
    fun normalize_deduplicatesByBssidAndUsesNewestObservation() {
        val results = WifiAnalyzerRepository.normalize(
            observations = listOf(
                observation(ssid = "Old", levelDbm = -40, lastSeenMillis = 100),
                observation(ssid = "New", levelDbm = -65, lastSeenMillis = 200),
            ),
            connectedBssid = null,
            savedBssids = emptySet(),
        )

        assertEquals(1, results.size)
        assertEquals("New", results.single().ssid)
        assertEquals(-65, results.single().levelDbm)
    }

    @Test
    fun normalize_marksConnectedAndSavedCaseInsensitivelyThenSortsBySignal() {
        val results = WifiAnalyzerRepository.normalize(
            observations = listOf(
                observation(ssid = "Strong", bssid = "aa:bb:cc:dd:ee:01", levelDbm = -35),
                observation(ssid = "Connected", bssid = "aa:bb:cc:dd:ee:02", levelDbm = -80),
                observation(ssid = "Saved", bssid = "aa:bb:cc:dd:ee:03", levelDbm = -55),
            ),
            connectedBssid = "AA:BB:CC:DD:EE:02",
            savedBssids = setOf("AA:BB:CC:DD:EE:03"),
        )

        assertEquals(listOf("Connected", "Strong", "Saved"), results.map(WifiAccessPoint::ssid))
        assertTrue(results[0].connected)
        assertTrue(results[2].saved)
    }

    @Test
    fun channelScores_includeCommonTwoPointFourCandidatesAndAdjacentOverlap() {
        val scores = WifiAnalyzerRepository.channelScores(
            accessPoints = listOf(
                accessPoint(channel = 1, levelDbm = -40),
                accessPoint(bssid = "aa:bb:cc:dd:ee:02", channel = 2, levelDbm = -50),
                accessPoint(bssid = "aa:bb:cc:dd:ee:03", channel = 6, levelDbm = -80),
            ),
            band = WifiBand.BAND_2_4_GHZ,
        )

        assertTrue(scores.map(WifiChannelScore::channel).containsAll(listOf(1, 6, 11)))
        assertTrue(scores.first { it.channel == 1 }.accessPointCount >= 2)
        assertEquals(0.0, scores.first { it.channel == 11 }.score, 0.001)
    }

    @Test
    fun channelScores_accountForWideChannelOverlap() {
        val wide = accessPoint(
            channel = 36,
            frequencyMhz = 5_180,
            band = WifiBand.BAND_5_GHZ,
            channelWidthMhz = 80,
        )
        val neighbor = accessPoint(
            bssid = "aa:bb:cc:dd:ee:02",
            channel = 40,
            frequencyMhz = 5_200,
            band = WifiBand.BAND_5_GHZ,
        )

        val scores = WifiAnalyzerRepository.channelScores(listOf(wide, neighbor), WifiBand.BAND_5_GHZ)

        assertEquals(2, scores.first { it.channel == 40 }.accessPointCount)
        assertTrue(scores.first { it.channel == 40 }.score > scores.first { it.channel == 36 }.score)
    }

    @Test
    fun signalQuality_isBoundedAndLabelsExpectedRanges() {
        assertEquals(100, accessPoint(levelDbm = 0).signalQuality)
        assertEquals(0, accessPoint(levelDbm = -127).signalQuality)
        assertEquals("Excellent", accessPoint(levelDbm = -45).signalLabel)
        assertEquals("Good", accessPoint(levelDbm = -55).signalLabel)
        assertEquals("Fair", accessPoint(levelDbm = -65).signalLabel)
        assertEquals("Weak", accessPoint(levelDbm = -80).signalLabel)
    }

    @Test
    fun plainText_containsIdentityRadioAndSecurityDetails() {
        val text = WifiScanSnapshot(
            accessPoints = listOf(accessPoint()),
            stale = false,
            scannedAtMillis = 1,
        ).asPlainText()

        assertTrue(text.contains("Access points: 1"))
        assertTrue(text.contains("BSSID: aa:bb:cc:dd:ee:01"))
        assertTrue(text.contains("Channel: 1"))
        assertTrue(text.contains("WPA2 Personal"))
    }

    private fun observation(
        ssid: String? = "Test Wi-Fi",
        bssid: String? = "aa:bb:cc:dd:ee:01",
        levelDbm: Int = -50,
        frequencyMhz: Int = 2_412,
        capabilities: String? = "[RSN-PSK-CCMP][ESS]",
        channelWidthCode: Int = 0,
        lastSeenMillis: Long = 100,
    ) = RawWifiObservation(
        ssid = ssid,
        bssid = bssid,
        levelDbm = levelDbm,
        frequencyMhz = frequencyMhz,
        capabilities = capabilities,
        channelWidthCode = channelWidthCode,
        lastSeenMillis = lastSeenMillis,
    )

    private fun accessPoint(
        ssid: String = "Test Wi-Fi",
        bssid: String = "aa:bb:cc:dd:ee:01",
        levelDbm: Int = -50,
        frequencyMhz: Int = 2_412,
        channel: Int = 1,
        band: WifiBand = WifiBand.BAND_2_4_GHZ,
        channelWidthMhz: Int = 20,
    ) = WifiAccessPoint(
        ssid = ssid,
        hidden = false,
        bssid = bssid,
        levelDbm = levelDbm,
        frequencyMhz = frequencyMhz,
        channel = channel,
        band = band,
        security = "WPA2 Personal",
        channelWidth = "$channelWidthMhz MHz",
        channelWidthMhz = channelWidthMhz,
        lastSeenMillis = 100,
    )
}
