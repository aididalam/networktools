package com.nexdevlab.networktools

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class RouterSetupScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun cardOpensDetectedGatewayAndSettingsActions() {
        openRouterSetup()

        composeRule.onNodeWithText("Detected").assertIsDisplayed()
        composeRule.onNodeWithText("Manual").assertIsDisplayed()
        composeRule.onNodeWithText("HTTPS").assertIsDisplayed()
        composeRule.onNodeWithText("Wi-Fi settings").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Private DNS").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun manualPrivateAddressShowsExactHttpsConfirmation() {
        enterManualAddress("192.168.50.1")
        composeRule.onNodeWithText("Open router administration").performClick()

        composeRule.onNodeWithText("Open Router administration?").assertIsDisplayed()
        composeRule.onNodeWithText("https://192.168.50.1").assertIsDisplayed()
        composeRule.onNodeWithText("Open in browser").assertIsDisplayed()
    }

    @Test
    fun publicAddressIsRejectedBeforeNavigation() {
        enterManualAddress("8.8.8.8")
        composeRule.onNodeWithText("Open router administration").performClick()

        composeRule.onNodeWithText("Router address must be a private or link-local IP address").assertIsDisplayed()
        composeRule.onNodeWithText("Open in browser").assertDoesNotExist()
    }

    @Test
    fun urlInputIsRejectedBeforeNavigation() {
        enterManualAddress("https://192.168.1.1")
        composeRule.onNodeWithText("Open router administration").performClick()

        composeRule.onNodeWithText("Enter one numeric router IP address without a URL, path, port, or credentials")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Open in browser").assertDoesNotExist()
    }

    @Test
    fun httpSelectionShowsWarningInFormAndConfirmation() {
        enterManualAddress("10.0.0.1")
        composeRule.onNodeWithText("HTTP", useUnmergedTree = true).performClick()

        composeRule.onNodeWithText("HTTP is unencrypted. Use it only when your router does not support HTTPS.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Open router administration").performClick()
        composeRule.onNodeWithText("http://10.0.0.1").assertIsDisplayed()
        composeRule.onNodeWithText("Warning: HTTP is unencrypted. Router credentials could be exposed on the network.")
            .assertIsDisplayed()
    }

    @Test
    fun routerSubpageUsesSameConfirmedBaseAddress() {
        enterManualAddress("172.16.0.1")
        composeRule.onNodeWithTag("router_setup_list").performScrollToIndex(7)
        composeRule.onNodeWithText("Connected devices").performClick()

        composeRule.onNodeWithText("Open Connected devices?").assertIsDisplayed()
        composeRule.onNodeWithText("https://172.16.0.1").assertIsDisplayed()
    }

    private fun openRouterSetup() {
        composeRule.onNodeWithText("Router Setup").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Open router administration").assertIsDisplayed()
    }

    private fun enterManualAddress(address: String) {
        openRouterSetup()
        composeRule.onNodeWithText("Manual").performClick()
        composeRule.onNodeWithText("Private router IP address").performTextInput(address)
    }
}
