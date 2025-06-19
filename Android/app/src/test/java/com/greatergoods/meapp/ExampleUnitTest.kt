package com.greatergoods.meapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.greatergoods.meapp.features.common.components.MainBottomNav
import com.greatergoods.meapp.features.dashboard.enum.BOTTOM_NAV_ITEMS
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}

class MainBottomNavTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Test that selecting an item calls the callback with the correct index and item.
     */
    @Test
    fun testItemSelectionCallback() {
        var selectedIndex = -1
        var selectedLabel = ""
        composeTestRule.setContent {
            MainBottomNav(
            )
        }
        composeTestRule.onNodeWithText(BOTTOM_NAV_ITEMS[1].label).performClick()
        assert(selectedIndex == 1)
        assert(selectedLabel == BOTTOM_NAV_ITEMS[1].label)
    }

    /**
     * Test that badge is displayed for items with isBadgeVisible = true.
     */
    @Test
    fun testBadgeDisplay() {
        composeTestRule.setContent {
            MainBottomNav()
        }
        // Badge count for first item is 3 (see BOTTOM_NAV_ITEMS)
        composeTestRule.onNodeWithText("3").assertIsDisplayed()
    }

    /**
     * Test that the selected item is visually indicated (label is present).
     */
    @Test
    fun testSelectedItemLabelDisplayed() {
        composeTestRule.setContent {
            MainBottomNav()
        }
        composeTestRule.onNodeWithText(BOTTOM_NAV_ITEMS[2].label).assertIsDisplayed()
    }
}
