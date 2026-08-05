package com.nexdevlab.networktools

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexdevlab.networktools.data.MlabNdt7Client
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MlabNdt7ClientTest {
    private val machine = "ndt-test-01.measurement-lab.org"
    private val download = "wss://$machine/ndt/v7/download?access_token=signed-download"
    private val upload = "wss://$machine/ndt/v7/upload?access_token=signed-upload"

    @Test fun locateResponseAcceptsBoundedSignedMlabEndpoints() {
        val json = """{"results":[{"machine":"$machine","location":{"city":"Dhaka","country":"BD"},"urls":{"wss:///ndt/v7/download":"$download","wss:///ndt/v7/upload":"$upload"}}]}"""
        val servers = MlabNdt7Client.parseLocateResponse(json, "BD")
        assertEquals(1, servers.size)
        assertEquals("Dhaka", servers.single().city)
        assertEquals("BD", servers.single().countryCode)
    }

    @Test fun locateResponseEnforcesStrictCountryAndMlabHost() {
        val wrongCountry = """{"results":[{"machine":"$machine","location":{"city":"Dhaka","country":"BD"},"urls":{"wss:///ndt/v7/download":"$download","wss:///ndt/v7/upload":"$upload"}}]}"""
        assertTrue(MlabNdt7Client.parseLocateResponse(wrongCountry, "SG").isEmpty())
        assertEquals(1, MlabNdt7Client.parseLocateResponse(wrongCountry).size)
        assertFalse(MlabNdt7Client.validEndpoint(
            "wss://example.com/ndt/v7/download?access_token=signed",
            "example.com",
            "/ndt/v7/download",
        ))
    }

    @Test fun endpointRejectsMissingTokenWrongPathAndCleartext() {
        assertFalse(MlabNdt7Client.validEndpoint("wss://$machine/ndt/v7/download", machine, "/ndt/v7/download"))
        assertFalse(MlabNdt7Client.validEndpoint(upload, machine, "/ndt/v7/download"))
        assertFalse(MlabNdt7Client.validEndpoint(
            "ws://$machine/ndt/v7/download?access_token=signed",
            machine,
            "/ndt/v7/download",
        ))
    }
}
