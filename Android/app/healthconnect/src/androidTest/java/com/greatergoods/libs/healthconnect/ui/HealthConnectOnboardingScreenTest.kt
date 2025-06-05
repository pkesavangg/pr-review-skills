package com.greatergoods.libs.healthconnect.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/**
 * Instrumentation tests for [HealthConnectOnboardingScreen].
 */
class HealthConnectOnboardingScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testOnboardingScreen_showsPermissionStatusAndButton() {
        var clicked = false
        composeTestRule.setContent {
            HealthConnectOnboardingScreen(
                permissionStatus = "NONE",
                onRequestPermissions = { clicked = true }
            )
        }
        composeTestRule.onNodeWithText("Current permission status: NONE").assertExists()
        composeTestRule.onNodeWithText("Grant Permissions").performClick()
        assert(clicked)
    }
}
