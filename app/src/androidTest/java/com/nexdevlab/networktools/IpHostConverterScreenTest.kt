package com.nexdevlab.networktools

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class IpHostConverterScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun cardOpensHostToIpFormWithoutQuery() {
        openConverter()

        composeRule.onNodeWithText("Host to IP").assertIsDisplayed()
        composeRule.onNodeWithText("IP to host").assertIsDisplayed()
        composeRule.onNodeWithText("Hostname").assertIsDisplayed()
        composeRule.onNodeWithText("Both").assertIsDisplayed()
        composeRule.onNodeWithText("System DNS").assertIsDisplayed()
        composeRule.onNodeWithText("No conversion result").assertIsDisplayed()
    }

    @Test
    fun forwardModeOffersIpv4Ipv6AndBoth() {
        openConverter()

        composeRule.onNodeWithText("Both").assertIsDisplayed()
        composeRule.onNodeWithText("IPv4").assertIsDisplayed()
        composeRule.onNodeWithText("IPv6").assertIsDisplayed()
        composeRule.onNodeWithText("IPv6").performClick()
    }

    @Test
    fun reverseModeRequiresOneNumericAddress() {
        openConverter()
        composeRule.onNodeWithText("IP to host").performClick()

        composeRule.onNodeWithText("IPv4 or IPv6 address").assertIsDisplayed()
        composeRule.onNodeWithText("Both").assertDoesNotExist()
    }

    @Test
    fun customResolverShowsNumericFieldAndFreePresets() {
        openConverter()
        composeRule.onNodeWithText("Custom DNS").performClick()

        composeRule.onNodeWithText("DNS-server IP address").assertIsDisplayed()
        composeRule.onNodeWithText("Cloudflare").assertIsDisplayed()
        composeRule.onNodeWithText("Google").assertIsDisplayed()
        composeRule.onNodeWithText("Quad9").assertIsDisplayed()
    }

    @Test
    fun blankHostShowsValidationWithoutNetworkRequest() {
        openConverter()
        composeRule.onNodeWithText("Convert").performScrollTo().performClick()

        composeRule.onNodeWithText("Enter a domain name or DNS name").assertIsDisplayed()
    }

    @Test
    fun hostnameInReverseModeShowsNumericValidation() {
        openConverter()
        composeRule.onNodeWithText("IP to host").performClick()
        composeRule.onNodeWithText("IPv4 or IPv6 address").performTextInput("example.com")
        composeRule.onNodeWithText("Convert").performScrollTo().performClick()

        composeRule.onNodeWithText("Enter a numeric IPv4 or IPv6 address").assertIsDisplayed()
    }

    private fun openConverter() {
        composeRule.onNodeWithText("IP & Host Converter").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Convert").assertIsDisplayed()
    }
}
