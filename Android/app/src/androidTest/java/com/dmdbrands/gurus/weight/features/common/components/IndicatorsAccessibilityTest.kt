package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.dmdbrands.gurus.weight.features.common.components.strings.AppLoaderStrings
import com.dmdbrands.gurus.weight.features.common.components.strings.ConnectionIndicatorStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the indicator/media components' TalkBack semantics
 * (MOB-850 — Phase 0, Batch F).
 *
 * AppLinearProgressIndicator (Material3 determinate progress) and AppGifImage (nullable
 * contentDescription) are already accessible; the progress one is pinned with a guard test.
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class IndicatorsAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun connectionIndicator_connectingStateIsAnnounced() {
        composeTestRule.setContent {
            MeAppTheme {
                ConnectionIndicator(
                    indicatorIcon = AppIcons.Default.WifiIndicator,
                    connectionState = ConnectionIndicatorState.Connecting,
                    showIndicatorAlone = true,
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(ConnectionIndicatorStrings.ConnectingDescription)
            .assertExists()
    }

    @Test
    fun connectionIndicator_failedStateIsAnnounced() {
        composeTestRule.setContent {
            MeAppTheme {
                ConnectionIndicator(
                    indicatorIcon = AppIcons.Default.ErrorIndicator,
                    connectionState = ConnectionIndicatorState.Failed,
                    showIndicatorAlone = true,
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(ConnectionIndicatorStrings.FailedDescription)
            .assertExists()
    }

    @Test
    fun appLoader_announcesLoadingWhenNoMessage() {
        composeTestRule.setContent {
            MeAppTheme {
                AppLoader(isLoading = true, style = LoaderStyle.CIRCULAR)
            }
        }

        composeTestRule
            .onNodeWithContentDescription(AppLoaderStrings.accLoadingLabel)
            .assertExists()
    }

    @Test
    fun appLoader_readsMessageAndDoesNotDuplicateLoading() {
        composeTestRule.setContent {
            MeAppTheme {
                AppLoader(isLoading = true, message = "Saving entry", style = LoaderStyle.CIRCULAR)
            }
        }

        composeTestRule.onNodeWithText("Saving entry").assertExists()
        // The "Loading" fallback must not be added when a message is shown.
        composeTestRule.onNodeWithContentDescription(AppLoaderStrings.accLoadingLabel).assertDoesNotExist()
    }

    @Test
    fun appLinearProgressIndicator_exposesProgressRangeInfo() {
        composeTestRule.setContent {
            MeAppTheme {
                AppLinearProgressIndicator(progress = 0.5f)
            }
        }

        // Material3 determinate progress exposes ProgressBarRangeInfo (TalkBack reads the %).
        composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertExists()
    }
}
