package com.nexdevlab.networktools

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class WakeOnLanScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun cardOpensSavedDeviceEmptyState() {
        openWakeOnLan()

        composeRule.onNodeWithText("Local network").assertIsDisplayed()
        composeRule.onNodeWithText("No saved devices").assertIsDisplayed()
        composeRule.onNodeWithText("Add device").assertIsDisplayed()
    }

    @Test
    fun localEditorShowsAllSavedPacketFields() {
        openEditor()

        composeRule.onNodeWithText("Local").assertIsDisplayed()
        composeRule.onNodeWithText("Remote").assertIsDisplayed()
        composeRule.onNodeWithText("Friendly device name").assertIsDisplayed()
        composeRule.onNodeWithText("MAC address").assertIsDisplayed()
        composeRule.onNodeWithText("Device IPv4 address").assertIsDisplayed()
        composeRule.onNodeWithText("Broadcast address").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("UDP port").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun remoteEditorReplacesBroadcastWithPublicDestinationGuidance() {
        openEditor()
        composeRule.onNodeWithText("Remote").performClick()

        composeRule.onNodeWithText("Public host or IP address").assertIsDisplayed()
        composeRule.onNodeWithText("Broadcast address").assertDoesNotExist()
        composeRule.onNodeWithText(
            "Remote Wake-on-LAN requires router UDP forwarding or your own compatible relay. " +
                "One packet is sent to the displayed public destination.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun malformedMacIsRejectedBeforeConfirmation() {
        openEditor()
        fillRequiredLocalFields(mac = "invalid")
        composeRule.onNodeWithText("Review & send").performScrollTo().performClick()

        composeRule.onNodeWithText("Enter a 12-digit MAC address").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Send packet").assertDoesNotExist()
    }

    @Test
    fun localReviewShowsExactMacAndOnePacketAuthorization() {
        openEditor()
        fillRequiredLocalFields(mac = "00:11:22:33:44:55")
        composeRule.onNodeWithText("Review & send").performScrollTo().performClick()

        composeRule.onNodeWithText("Send magic packet?").assertIsDisplayed()
        composeRule.onNodeWithText("MAC  00:11:22:33:44:55").assertIsDisplayed()
        composeRule.onNodeWithText("I am authorized to wake this device").assertIsDisplayed()
        composeRule.onNodeWithText("Send packet").assertIsNotEnabled()
    }

    @Test
    fun remoteReviewDisplaysExactDestinationAndRequiresAuthorization() {
        openEditor()
        composeRule.onNodeWithText("Remote").performClick()
        composeRule.onNodeWithText("Friendly device name").performTextInput("Remote PC")
        composeRule.onNodeWithText("MAC address").performTextInput("02:11:22:33:44:55")
        composeRule.onNodeWithText("Public host or IP address").performTextInput("wol.example.com")
        composeRule.onNodeWithText("Review & send").performScrollTo().performClick()

        composeRule.onNodeWithText("To   wol.example.com:9").assertIsDisplayed()
        composeRule.onNodeWithText("Send packet").assertIsNotEnabled()
        composeRule.onNodeWithText("I am authorized to wake this device").performClick()
        composeRule.onNodeWithText("Send packet").assertIsEnabled()
    }

    private fun openWakeOnLan() {
        composeRule.onNodeWithTag("tool_grid").performScrollToIndex(12)
        composeRule.onNodeWithText("Wake-on-LAN").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Local network").assertIsDisplayed()
    }

    private fun openEditor() {
        openWakeOnLan()
        composeRule.onNodeWithText("Add device").performClick()
        composeRule.onNodeWithText("Friendly device name").assertIsDisplayed()
    }

    private fun fillRequiredLocalFields(mac: String) {
        composeRule.onNodeWithText("Friendly device name").performTextInput("Office PC")
        composeRule.onNodeWithText("MAC address").performTextInput(mac)
        composeRule.onNodeWithText("Device IPv4 address").performTextInput("192.168.0.50")
    }
}
