package com.dmdbrands.gurus.weight.features.loading

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the Loading splash screen TalkBack semantics
 * (MOB-851 — Phase 1, Auth/Loading).
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class LoadingScreenAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loadingText_announcedViaPoliteLiveRegion() {
        composeTestRule.setContent {
            MeAppTheme {
                LoadingTextWithDots(baseText = "loading")
            }
        }

        // The merged row carries the "loading" text and a polite live region so the
        // splash state is announced; the animated dots are cleared (not spelled out).
        composeTestRule.onNodeWithText("loading")
            .assert(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion),
            )
    }
}
