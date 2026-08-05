package com.nexdevlab.networktools

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class WifiAnalyzerScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun cardOpensWithoutScanningOrPrompting() {
        openWifiAnalyzer()

        composeRule.onNodeWithText("Ready to scan").assertIsDisplayed()
        composeRule.onNodeWithText("Scan nearby Wi-Fi").assertIsDisplayed()
        composeRule.onAllNodesWithText("Allow Wi-Fi scanning").assertCountEquals(0)
    }

    @Test
    fun channelsAndSavedViewsHaveUsefulEmptyStates() {
        openWifiAnalyzer()

        composeRule.onNodeWithText("Channels").performClick()
        composeRule.onNodeWithText("Ready to scan").assertIsDisplayed()
        composeRule.onNodeWithText("Saved").performClick()
        composeRule.onNodeWithText("Open Android saved networks").assertIsDisplayed()
        composeRule.onNodeWithText("Observed in NetworkTools").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Connected networks are recorded when scanned. Android Settings owns the complete saved list, passwords, and QR sharing.",
        )
            .assertIsDisplayed()
    }

    @Test
    fun scanExplainsSensitivePermissionBeforeSystemRequest() {
        openWifiAnalyzer()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            composeRule.onNodeWithText("Scan nearby Wi-Fi").performClick()
            composeRule.onNodeWithText("Allow Wi-Fi scanning").assertIsDisplayed()
            composeRule.onNodeWithText("Continue").assertIsDisplayed()
            composeRule.onNodeWithText("Not now").performClick()
            composeRule.onNodeWithText("Ready to scan").assertIsDisplayed()
        }
    }

    @Test
    fun savedNetworksRequireConsentBeforeOpeningAndroidSettings() {
        openWifiAnalyzer()
        composeRule.onNodeWithText("Saved").performClick()
        composeRule.onNodeWithText("Open Android saved networks").performClick()

        composeRule.onNodeWithText("Open Android Wi-Fi sharing?").assertIsDisplayed()
        composeRule.onNodeWithText("Open Wi-Fi settings").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Observed in NetworkTools").assertIsDisplayed()
    }

    private fun openWifiAnalyzer() {
        composeRule.onNodeWithText("Wi-Fi Analyzer").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Scan nearby Wi-Fi networks").assertIsDisplayed()
    }
}
