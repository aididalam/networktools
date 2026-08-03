package com.nexdevlab.networktools

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class IpFinderScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun cardOpensPublicIpFormWithoutStartingLookup() {
        openFinder()

        composeRule.onNodeWithText("Public IP").assertIsDisplayed()
        composeRule.onNodeWithText("Domain or IP").assertIsDisplayed()
        composeRule.onNodeWithText("System DNS").assertIsDisplayed()
        composeRule.onNodeWithText("Custom DNS").assertIsDisplayed()
        composeRule.onNodeWithText("No IP Finder result").assertIsDisplayed()
        composeRule.onNodeWithText("Domain or public IP address").assertDoesNotExist()
    }

    @Test
    fun targetModeShowsSingleBoundedTargetField() {
        openFinder()
        composeRule.onNodeWithText("Domain or IP").performClick()

        composeRule.onNodeWithText("Domain or public IP address").assertIsDisplayed()
        composeRule.onNodeWithText("Find IP").assertIsDisplayed()
    }

    @Test
    fun customResolverShowsNumericFieldAndFreePresets() {
        openFinder()
        composeRule.onNodeWithText("Custom DNS").performClick()

        composeRule.onNodeWithText("DNS-server IP address").assertIsDisplayed()
        composeRule.onNodeWithText("Cloudflare").assertIsDisplayed()
        composeRule.onNodeWithText("Google").assertIsDisplayed()
        composeRule.onNodeWithText("Quad9").assertIsDisplayed()
    }

    @Test
    fun blankTargetShowsValidationWithoutNetworkRequest() {
        openFinder()
        composeRule.onNodeWithText("Domain or IP").performClick()
        composeRule.onNodeWithText("Find IP").performScrollTo().performClick()

        composeRule.onNodeWithText("Enter a domain or public IP address").assertIsDisplayed()
    }

    @Test
    fun malformedNumericTargetShowsValidationWithoutNetworkRequest() {
        openFinder()
        composeRule.onNodeWithText("Domain or IP").performClick()
        composeRule.onNodeWithText("Domain or public IP address").performTextInput("999.1.1.1")
        composeRule.onNodeWithText("Find IP").performScrollTo().performClick()

        composeRule.onNodeWithText("Every IPv4 octet must be from 0-255").assertIsDisplayed()
    }

    @Test
    fun urlTargetIsRejectedWithoutNetworkRequest() {
        openFinder()
        composeRule.onNodeWithText("Domain or IP").performClick()
        composeRule.onNodeWithText("Domain or public IP address").performTextInput("https://example.com")
        composeRule.onNodeWithText("Find IP").performScrollTo().performClick()

        composeRule.onNodeWithText("Enter a domain or numeric public IP without a URL, path, or command characters")
            .performScrollTo().assertIsDisplayed()
    }

    private fun openFinder() {
        composeRule.onNodeWithText("IP Finder").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Find IP").assertIsDisplayed()
    }
}
