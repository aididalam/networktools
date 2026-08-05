package com.nexdevlab.networktools

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performScrollToIndex
import com.nexdevlab.networktools.data.SpeedTestPolicyStore
import org.junit.Rule
import org.junit.Test

class InternetSpeedMeterScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun speedMeterOffersCountriesAndExplicitConsentBeforeTesting() {
        composeRule.activity.getSharedPreferences(SpeedTestPolicyStore.PreferencesName, Context.MODE_PRIVATE)
            .edit().clear().commit()
        composeRule.onNodeWithTag("tool_grid").performScrollToIndex(14)
        composeRule.onNodeWithText("Internet Speed Meter").performClick()
        composeRule.onNodeWithTag("speed_endpoint_selector").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("speed_endpoint_automatic").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("speed_country_selector").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("All countries · servers load live").assertIsDisplayed()
        composeRule.onNodeWithTag("speed_country_search").performTextInput("zzzz")
        composeRule.onNodeWithText("No countries found").assertIsDisplayed()
        composeRule.onNodeWithTag("speed_country_search").performTextClearance()
        composeRule.onNodeWithTag("speed_country_search").performTextInput("local")
        composeRule.onNodeWithTag("speed_country_choice_local").assertIsDisplayed()
        composeRule.onNodeWithTag("speed_country_search").performTextClearance()
        composeRule.onNodeWithTag("speed_country_search").performTextInput("bang")
        composeRule.onNodeWithText("Bangladesh").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("speed_test_action").performClick()
        composeRule.onNodeWithText("Before Your Speed Test").assertIsDisplayed()
        composeRule.onNodeWithText("publicly publishes", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
    }
}
