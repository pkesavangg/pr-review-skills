package com.dmdbrands.gurus.weight

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dmdbrands.gurus.weight.features.common.components.MainBottomNav
import com.dmdbrands.gurus.weight.features.dashboard.enum.BOTTOM_NAV_ITEMS
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for [MainBottomNav].
 *
 * These tests require an Android device/emulator because they use the real
 * Compose test rule with a Compose host Activity.
 */
class MainBottomNavTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testItemSelectionCallback() {
        var selectedIndex = -1
        var selectedLabel = ""
        composeTestRule.setContent {
            MainBottomNav(
                showAppsync = false,
                onOpenAppSync = {},
            )
        }
        composeTestRule.onNodeWithText(BOTTOM_NAV_ITEMS[1].label).performClick()
        assert(selectedIndex == 1)
        assert(selectedLabel == BOTTOM_NAV_ITEMS[1].label)
    }

    @Test
    fun testBadgeDisplay() {
        composeTestRule.setContent {
            MainBottomNav(
                showAppsync = false,
                onOpenAppSync = {},
            )
        }
        composeTestRule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun testSelectedItemLabelDisplayed() {
        composeTestRule.setContent {
            MainBottomNav(
                showAppsync = false,
                onOpenAppSync = {},
            )
        }
        composeTestRule.onNodeWithText(BOTTOM_NAV_ITEMS[2].label).assertIsDisplayed()
    }
}
