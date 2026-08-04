package com.nexdevlab.networktools

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import org.junit.Rule
import org.junit.Test

class ConnectionsLogScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun screenShowsLiveStateFiltersAndLocalRetentionBoundary() {
        composeRule.onNodeWithTag("tool_grid").performScrollToIndex(13)
        composeRule.onNodeWithText("Connections Log").performClick()
        composeRule.onNodeWithText("Monitoring this screen").assertIsDisplayed()
        composeRule.onNodeWithText("Wi-Fi").performClick()
        composeRule.onNodeWithText("Mobile").performClick()
        composeRule.onNodeWithText("All").performClick()
        composeRule.onNodeWithText("Stored locally:", substring = true).performScrollTo().assertIsDisplayed()
    }
}

