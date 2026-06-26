package com.dmdbrands.gurus.weight.features.ScaleSetup

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.BabyScaleLoader
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ErrorContent
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleSetupHeader
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.ScaleSetupLoader
import com.dmdbrands.gurus.weight.features.ScaleSetup.components.SetupContent
import com.dmdbrands.gurus.weight.features.ScaleSetup.enums.LoaderIconType
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BabyScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.SetupLoaderStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.WifiScaleSetupStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the Add Scale & Scale Setup flows' TalkBack
 * semantics (MOB-855 — Phase 5).
 *
 * Covers the dependency-light shared building blocks that every setup screen
 * (Bt / Wifi / BtWifi / Lcbt / AppSync / Baby / Monitor) composes: step titles are
 * headings, the app-bar close/help icon buttons carry labels, completion images carry
 * a meaningful description, and connection status / error codes are announced.
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class ScaleSetupAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun isHeading() = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    fun setupContent_titleIsAHeading() {
        composeTestRule.setContent {
            MeAppTheme {
                SetupContent(title = "Pairing Mode", subtitle = "Hold the button.")
            }
        }

        composeTestRule.onNodeWithText("Pairing Mode").assert(isHeading())
    }

    @Test
    fun setupContent_completionImageHasMeaningfulDescription() {
        composeTestRule.setContent {
            MeAppTheme {
                SetupContent(title = "All set", setupFinished = true)
            }
        }

        composeTestRule.onNodeWithContentDescription(ScaleSetupStrings.accSetupCompleteImage)
            .assertExists()
    }

    @Test
    fun scaleSetupHeader_iconButtonsAreLabeled() {
        composeTestRule.setContent {
            MeAppTheme {
                ScaleSetupHeader(sku = "0412", onBack = {}, onHelp = {}) {}
            }
        }

        composeTestRule.onNodeWithContentDescription(ScaleSetupStrings.accCloseButton).assertExists()
        composeTestRule.onNodeWithContentDescription(ScaleSetupStrings.accHelpButton).assertExists()
    }

    @Test
    fun errorContent_titleWithTCodeIsAHeading() {
        composeTestRule.setContent {
            MeAppTheme {
                ErrorContent(errorCode = "t163")
            }
        }

        composeTestRule.onNodeWithText(
            "${WifiScaleSetupStrings.ErrorDetail.Troubleshooting} - t163",
        ).assert(isHeading())
    }

    @Test
    fun scaleSetupLoader_titleIsHeadingAndErrorCodeIsLiveRegion() {
        composeTestRule.setContent {
            MeAppTheme {
                ScaleSetupLoader(
                    title = "Connection Error",
                    errorCode = "ERR_001",
                    connectionState = ConnectionState.Failed.Error,
                    showIndicationOnly = true,
                    indicatorIcon = LoaderIconType.Error,
                )
            }
        }

        composeTestRule.onNodeWithText("Connection Error").assert(isHeading())
        composeTestRule.onNodeWithText("${SetupLoaderStrings.ErrorCodeLabel}ERR_001")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun babyScaleLoader_titleIsHeadingAndGifIsLabeled() {
        composeTestRule.setContent {
            MeAppTheme {
                BabyScaleLoader(title = "Turn on your Scale", subtitle = "Step on it to wake it up.")
            }
        }

        composeTestRule.onNodeWithText("Turn on your Scale").assert(isHeading())
        composeTestRule.onNodeWithContentDescription(BabyScaleSetupStrings.accSearchingLoader)
            .assertExists()
    }
}
