package com.dmdbrands.gurus.weight

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.features.common.components.MainBottomNav
import com.dmdbrands.gurus.weight.features.dashboard.enum.BOTTOM_NAV_ITEMS
import com.dmdbrands.gurus.weight.features.dashboard.string.DashboardString
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.example.nav3integration.TopLevelBackStack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private fun createBackStack(): TopLevelBackStack<NavKey> =
        TopLevelBackStack(
            startKey = AppRoute.Home to AppRoute.Main.Dashboard,
            loginKey = AppRoute.Init.Loading,
        )

    private fun setContent(
        showAppsync: Boolean = false,
        showUnreadFeedIndicator: Boolean = false,
        badgeVisible: List<AppRoute> = emptyList(),
        onOpenAppSync: () -> Unit = {},
    ) {
        val backStack = createBackStack()
        composeTestRule.setContent {
            MeAppTheme {
                CompositionLocalProvider(LocalNavBackStack provides backStack) {
                    MainBottomNav(
                        badgeVisible = badgeVisible,
                        showAppsync = showAppsync,
                        onOpenAppSync = onOpenAppSync,
                        showUnreadFeedIndicator = showUnreadFeedIndicator,
                    )
                }
            }
        }
    }

    @Test
    fun allNonAppsyncLabelsAreDisplayed() {
        setContent(showAppsync = false)

        BOTTOM_NAV_ITEMS
            .filter { it.label != DashboardString.BottomNav.appsync }
            .forEach { item ->
                composeTestRule.onNodeWithText(item.label).assertIsDisplayed()
            }
    }

    @Test
    fun appsyncLabelIsDisplayedWhenShowAppsyncTrue() {
        setContent(showAppsync = true)

        composeTestRule
            .onNodeWithText(DashboardString.BottomNav.appsync)
            .assertIsDisplayed()
    }

    @Test
    fun appsyncLabelIsHiddenWhenShowAppsyncFalse() {
        setContent(showAppsync = false)

        composeTestRule
            .onNodeWithText(DashboardString.BottomNav.appsync)
            .assertDoesNotExist()
    }

    @Test
    fun clickingAppsyncCallsOnOpenAppSync() {
        var called = false
        setContent(showAppsync = true, onOpenAppSync = { called = true })

        composeTestRule
            .onNodeWithText(DashboardString.BottomNav.appsync)
            .performClick()

        composeTestRule.waitForIdle()
        assertTrue("onOpenAppSync should have been called", called)
    }

    @Test
    fun clickingNonAppsyncItemDoesNotCallOnOpenAppSync() {
        var called = false
        setContent(showAppsync = false, onOpenAppSync = { called = true })

        composeTestRule
            .onNodeWithText(DashboardString.BottomNav.history)
            .performClick()

        composeTestRule.waitForIdle()
        assertFalse("onOpenAppSync should not have been called", called)
    }
}
