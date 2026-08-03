package com.nexdevlab.networktools

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.nexdevlab.networktools.data.LanScanHistoryStore
import com.nexdevlab.networktools.data.LanScannerRepository
import org.junit.Rule
import org.junit.Test

class LanScannerScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun cardOpensBoundedScannerWithoutStarting() {
        openLanScanner()

        composeRule.onNodeWithText("Private IPv4 subnet").assertIsDisplayed()
        composeRule.onNodeWithText("Maximum 254 hosts (/24)").assertIsDisplayed()
        composeRule.onNodeWithText("Fast").assertIsDisplayed()
        composeRule.onNodeWithText("Deep").assertIsDisplayed()
        composeRule.onNodeWithText("I own or am authorized to scan this local network").assertIsDisplayed()
        composeRule.onNodeWithText("No LAN scan result").assertIsDisplayed()
    }

    @Test
    fun startRequiresAuthorizationBeforePermissionRequest() {
        openLanScanner()
        composeRule.onNodeWithText("Start fast scan").performClick()

        composeRule.onNodeWithText("Confirm that you are authorized to scan this network").assertIsDisplayed()
    }

    @Test
    fun validStartExplainsLocalNetworkPermissionWhenNotGranted() {
        openLanScanner()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (!LanScannerRepository.hasLocalNetworkPermission(context)) {
            composeRule.onNodeWithText("I own or am authorized to scan this local network").performClick()
            composeRule.onNodeWithText("Start fast scan").performClick()
            composeRule.onNodeWithText("Allow local network access").assertIsDisplayed()
            composeRule.onNodeWithText("Not now").performClick()
            composeRule.onNodeWithText("No LAN scan result").assertIsDisplayed()
        }
    }

    @Test
    fun historyViewHasClearEmptyState() {
        LanScanHistoryStore(InstrumentationRegistry.getInstrumentation().targetContext).clear()
        openLanScanner()
        composeRule.onNodeWithText("History").performClick()

        composeRule.onNodeWithText("LAN scan history").assertIsDisplayed()
        composeRule.onNodeWithText("Completed and stopped scans will appear here.").assertIsDisplayed()
    }

    private fun openLanScanner() {
        composeRule.onNodeWithText("LAN Scanner").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Detect active subnet").assertIsDisplayed()
    }
}
