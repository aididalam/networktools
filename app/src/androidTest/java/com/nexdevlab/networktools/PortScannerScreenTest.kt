package com.nexdevlab.networktools

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.nexdevlab.networktools.data.PortScanHistoryStore
import org.junit.Rule
import org.junit.Test

class PortScannerScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun cardOpensSinglePortFormWithoutStarting() {
        openPortScanner()

        composeRule.onNodeWithText("Hostname or IP address").assertIsDisplayed()
        composeRule.onNodeWithText("Single").assertIsDisplayed()
        composeRule.onNodeWithText("Range").assertIsDisplayed()
        composeRule.onNodeWithText("Custom").assertIsDisplayed()
        composeRule.onNodeWithText("TCP port").assertIsDisplayed()
        composeRule.onNodeWithText("No port scan result").assertIsDisplayed()
    }

    @Test
    fun localShortcutStillRequiresAuthorization() {
        openPortScanner()
        composeRule.onNodeWithContentDescription("Use this device's LAN address").performClick()
        composeRule.onNodeWithText("Start port scan").performClick()

        composeRule.onNodeWithText("Confirm that you are authorized to scan this host").assertIsDisplayed()
    }

    @Test
    fun rangeAndCustomModesExposeExpectedInputs() {
        openPortScanner()
        composeRule.onNodeWithText("Range").performClick()
        composeRule.onNodeWithText("Start port").assertIsDisplayed()
        composeRule.onNodeWithText("End port").assertIsDisplayed()

        composeRule.onNodeWithText("Custom").performClick()
        composeRule.onNodeWithText("Comma-separated TCP ports").assertIsDisplayed()
        composeRule.onNodeWithText("Common ports").assertIsDisplayed()
    }

    @Test
    fun historyViewHasDeterministicEmptyState() {
        PortScanHistoryStore(InstrumentationRegistry.getInstrumentation().targetContext).clear()
        openPortScanner()
        composeRule.onNodeWithText("History").performClick()

        composeRule.onNodeWithText("Port scan history").assertIsDisplayed()
        composeRule.onNodeWithText("Completed and stopped port scans will appear here.").assertIsDisplayed()
    }

    private fun openPortScanner() {
        composeRule.onNodeWithText("Port Scanner").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Use this device's LAN address").assertIsDisplayed()
    }
}
