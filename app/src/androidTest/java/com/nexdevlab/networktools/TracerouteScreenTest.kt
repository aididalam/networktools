package com.nexdevlab.networktools

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nexdevlab.networktools.data.TracerouteRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class TracerouteScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun tracerouteCardOpensBoundedForm() {
        openTraceroute()

        composeRule.onNodeWithText("Hostname or IP address").assertIsDisplayed()
        composeRule.onNodeWithText("1-30").assertIsDisplayed()
        composeRule.onAllNodesWithText("1-3", useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithText("Start Traceroute").assertIsDisplayed()
    }

    @Test
    fun emptyTargetShowsInlineValidationWithoutStarting() {
        openTraceroute()
        composeRule.onNodeWithText("Start Traceroute").performClick()

        composeRule.onNodeWithText("Enter a hostname or IP address").assertIsDisplayed()
        composeRule.onNodeWithText("Start Traceroute").assertIsDisplayed()
    }

    @Test
    fun ripeStatParserAcceptsOnlySupportedValidCoordinates() {
        val valid = """
            {"status":"ok","data_call_status":"supported","data":{"located_resources":[
              {"locations":[{"country":"US","city":"","latitude":37.751,"longitude":-97.822}]}
            ]}}
        """.trimIndent()
        val invalid = """
            {"status":"ok","data_call_status":"development","data":{"located_resources":[
              {"locations":[{"country":"US","latitude":200,"longitude":-97.822}]}
            ]}}
        """.trimIndent()

        val location = TracerouteRepository.parseGeolocation(valid)
        assertEquals(37.751, location?.latitude ?: 0.0, 0.001)
        assertEquals(-97.822, location?.longitude ?: 0.0, 0.001)
        assertEquals("US", location?.countryCode)
        assertNull(TracerouteRepository.parseGeolocation(invalid))
    }

    private fun openTraceroute() {
        composeRule.onNodeWithText("Traceroute").performClick()
        composeRule.waitForIdle()
    }
}
