package com.nexdevlab.networktools

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class DnsLookupScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun cardOpensLookupFormWithoutStartingQuery() {
        openDnsLookup()

        composeRule.onNodeWithText("Domain or DNS name").assertIsDisplayed()
        composeRule.onNodeWithText("A record").assertIsDisplayed()
        composeRule.onNodeWithText("System DNS").assertIsDisplayed()
        composeRule.onNodeWithText("Custom DNS").assertIsDisplayed()
        composeRule.onNodeWithText("No DNS lookup result").assertIsDisplayed()
    }

    @Test
    fun recordTypeMenuProvidesPrimaryAndAdditionalTypes() {
        openDnsLookup()
        composeRule.onNodeWithContentDescription("Choose record type").performClick()

        composeRule.onNodeWithText("AAAA  IPv6 address").assertIsDisplayed()
        composeRule.onNodeWithText("MX  Mail exchange").assertIsDisplayed()
        composeRule.onNodeWithText("SOA  Zone authority").assertIsDisplayed()
        composeRule.onNodeWithText("PTR  Reverse lookup").assertIsDisplayed()
        composeRule.onNodeWithText("SRV  Service location").assertIsDisplayed()
        composeRule.onNodeWithText("CAA  Certificate authority").assertIsDisplayed()
    }

    @Test
    fun customResolverModeShowsNumericFieldAndFreePresets() {
        openDnsLookup()
        composeRule.onNodeWithText("Custom DNS").performClick()

        composeRule.onNodeWithText("DNS-server IP address").assertIsDisplayed()
        composeRule.onNodeWithText("Cloudflare").assertIsDisplayed()
        composeRule.onNodeWithText("Google").assertIsDisplayed()
        composeRule.onNodeWithText("Quad9").assertIsDisplayed()
    }

    @Test
    fun compareModeShowsBoundedResolverSetAndEmptyState() {
        openDnsLookup()
        composeRule.onNodeWithText("Compare").performClick()

        composeRule.onNodeWithText("System DNS + Cloudflare + Google + Quad9").assertIsDisplayed()
        composeRule.onNodeWithText("Compare resolvers").assertIsDisplayed()
        composeRule.onNodeWithText("No resolver comparison").assertIsDisplayed()
    }

    @Test
    fun blankQueryShowsValidationErrorWithoutNetworkRequest() {
        openDnsLookup()
        composeRule.onNodeWithText("Run DNS lookup").performClick()

        composeRule.onNodeWithText("Enter a domain name or DNS name").assertIsDisplayed()
    }

    private fun openDnsLookup() {
        composeRule.onNodeWithText("DNS Lookup").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Run DNS lookup").assertIsDisplayed()
    }
}
