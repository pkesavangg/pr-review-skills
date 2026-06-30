package com.dmdbrands.gurus.weight.features.landing

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.navigation3.runtime.NavKey
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.features.landing.screen.LandingScreen
import com.dmdbrands.gurus.weight.features.landing.strings.LandingString
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.example.nav3integration.TopLevelBackStack
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the Landing screen TalkBack semantics
 * (MOB-851 — Phase 1, Auth/Landing).
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class LandingScreenAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createBackStack(): TopLevelBackStack<NavKey> =
        TopLevelBackStack(
            startKey = AppRoute.Home to AppRoute.Main.Dashboard,
            loginKey = AppRoute.Init.Loading,
        )

    @Test
    fun landingLogo_readsBrandNameNotLoading() {
        composeTestRule.setContent {
            MeAppTheme {
                CompositionLocalProvider(LocalNavBackStack provides createBackStack()) {
                    LandingScreen()
                }
            }
        }

        // The brand banner now reads the product name (not the borrowed "loading" string).
        composeTestRule.onNodeWithContentDescription(LandingString.accLogoLabel)
            .assertExists()
    }
}
