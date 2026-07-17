package com.dmdbrands.gurus.weight.features.DeviceSetup

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.BabyScaleLoader
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DeviceSetupHeader
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DeviceSetupLoader
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.ErrorContent
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.SetupContent
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.LoaderIconType
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.ConnectionState
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.BabyScaleSetupStrings
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.DeviceSetupStrings
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.SetupLoaderStrings
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.WifiScaleSetupStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the Add Device & Device Setup flows' TalkBack
 * semantics (MOB-855 — Phase 5).
 *
 * Covers the dependency-light shared building blocks that every setup screen
 * (Bt / Wifi / BtWifi / Lcbt / AppSync / Baby / Monitor) composes: step titles are
 * headings, the app-bar close/help icon buttons carry labels, completion images carry
 * a meaningful description, and connection status / error codes are announced.
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class DeviceSetupAccessibilityTest {

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

        composeTestRule.onNodeWithContentDescription(DeviceSetupStrings.accSetupCompleteImage)
            .assertExists()
    }

    @Test
    fun deviceSetupHeader_iconButtonsAreLabeled() {
        composeTestRule.setContent {
            MeAppTheme {
                DeviceSetupHeader(sku = "0412", onBack = {}, onHelp = {}) {}
            }
        }

        composeTestRule.onNodeWithContentDescription(DeviceSetupStrings.accCloseButton).assertExists()
        composeTestRule.onNodeWithContentDescription(DeviceSetupStrings.accHelpButton).assertExists()
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
    fun deviceSetupLoader_titleIsHeadingAndErrorCodeIsLiveRegion() {
        composeTestRule.setContent {
            MeAppTheme {
                DeviceSetupLoader(
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
