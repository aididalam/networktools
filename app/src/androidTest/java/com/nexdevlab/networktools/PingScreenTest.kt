package com.nexdevlab.networktools

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class PingScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun pingCardOpensBoundedIcmpForm() {
        openPing()

        composeRule.onNodeWithText("ICMP").assertIsDisplayed()
        composeRule.onNodeWithText("Hostname or IP address").assertIsDisplayed()
        composeRule.onNodeWithText("1-10").assertIsDisplayed()
        composeRule.onNodeWithText("16-1400").assertIsDisplayed()
        composeRule.onNodeWithText("500-5000").assertIsDisplayed()
        composeRule.onNodeWithText("250-2000").assertIsDisplayed()
        composeRule.onNodeWithText("Start Ping").assertIsDisplayed()
    }

    @Test
    fun tcpModeShowsPortAndDisablesPacketSize() {
        openPing()
        composeRule.onNodeWithText("TCP").performClick()

        composeRule.onNodeWithText("TCP port").assertIsDisplayed()
        composeRule.onNodeWithText("1-65535").assertIsDisplayed()
        composeRule.onNodeWithText("ICMP only").assertIsDisplayed()
    }

    @Test
    fun emptyTargetShowsInlineValidationWithoutStarting() {
        openPing()
        composeRule.onNodeWithText("Start Ping").performClick()

        composeRule.onNodeWithText("Enter a hostname or IP address")
            .assertTextContains("Enter a hostname or IP address")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Start Ping").assertIsDisplayed()
    }

    private fun openPing() {
        composeRule.onNodeWithText("Ping").performClick()
        composeRule.waitForIdle()
    }
}
