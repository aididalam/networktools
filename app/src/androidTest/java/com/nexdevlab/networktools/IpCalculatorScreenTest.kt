package com.nexdevlab.networktools

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class IpCalculatorScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun cardOpensIpv4PrefixFormWithoutCalculation() {
        openCalculator()

        composeRule.onNodeWithText("IPv4").assertIsDisplayed()
        composeRule.onNodeWithText("IPv6").assertIsDisplayed()
        composeRule.onNodeWithText("IPv4 address").assertIsDisplayed()
        composeRule.onNodeWithText("Prefix").assertIsDisplayed()
        composeRule.onNodeWithText("Subnet mask").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Decrease prefix length").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Increase prefix length").assertIsDisplayed()
        composeRule.onNodeWithText("No subnet calculation").assertIsDisplayed()
    }

    @Test
    fun subnetMaskModeShowsMaskInput() {
        openCalculator()
        composeRule.onNodeWithText("Subnet mask").performClick()

        composeRule.onNodeWithText("255.255.255.0").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Decrease prefix length").assertDoesNotExist()
    }

    @Test
    fun ipv6ModeUses128BitPrefixForm() {
        openCalculator()
        composeRule.onNodeWithText("IPv6").performClick()

        composeRule.onNodeWithText("IPv6 address").assertIsDisplayed()
        composeRule.onNodeWithText("64").assertIsDisplayed()
        composeRule.onNodeWithText("Subnet mask").assertDoesNotExist()
    }

    @Test
    fun blankAddressShowsDeterministicValidationError() {
        openCalculator()
        composeRule.onNodeWithText("Calculate subnet").performScrollTo().performClick()

        composeRule.onNodeWithText("Enter an IP address, optionally followed by a CIDR prefix").assertIsDisplayed()
    }

    @Test
    fun nonContiguousSubnetMaskShowsValidationError() {
        openCalculator()
        composeRule.onNodeWithText("IPv4 address").performTextInput("192.168.1.10")
        composeRule.onNodeWithText("Subnet mask").performClick()
        composeRule.onNodeWithText("255.255.255.0").performTextClearance()
        composeRule.onNode(hasText("Subnet mask") and hasSetTextAction()).performTextInput("255.0.255.0")
        composeRule.onNodeWithText("Calculate subnet").performScrollTo().performClick()

        composeRule.onNodeWithText("Subnet mask must contain contiguous one bits").assertIsDisplayed()
    }

    @Test
    fun ipv4CalculationDisplaysNetworkCapacityAndMembership() {
        openCalculator()
        composeRule.onNodeWithText("IPv4 address").performTextInput("192.168.1.10")
        composeRule.onNodeWithText("Address to check (optional)").performTextInput("192.168.1.200")
        composeRule.onNodeWithText("Calculate subnet").performScrollTo().performClick()

        composeRule.onAllNodesWithText("192.168.1.0/24")[0].assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("192.168.1.255"))
        composeRule.onNodeWithText("192.168.1.255").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("254"))
        composeRule.onNodeWithText("254").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Address is inside subnet"))
        composeRule.onNodeWithText("Address is inside subnet").assertIsDisplayed()
    }

    private fun openCalculator() {
        composeRule.onNodeWithText("IP Calculator").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Calculate subnet").assertIsDisplayed()
    }
}
