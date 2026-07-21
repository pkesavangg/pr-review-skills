package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.signup.strings.DeviceReadyStrings
import com.dmdbrands.gurus.weight.features.signup.strings.PickDeviceStrings
import com.dmdbrands.gurus.weight.features.signup.strings.SignupStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the signup step components' TalkBack semantics
 * (MOB-851 — Phase 1, Auth/Signup).
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class SignupStepsAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun nameStep_titleIsAHeading() {
        composeTestRule.setContent {
            MeAppTheme {
                NameStep(
                    firstNameControl = FormControl.create("", listOf(FormValidations.required())),
                    lastNameControl = FormControl.create("", listOf(FormValidations.required())),
                )
            }
        }

        composeTestRule.onNodeWithText(SignupStrings.nameStepTitle)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun pickDeviceStep_titleIsAHeading() {
        composeTestRule.setContent {
            MeAppTheme {
                PickDeviceStep(
                    deviceControl = FormControl.create("", listOf(FormValidations.required())),
                )
            }
        }

        composeTestRule.onNodeWithText(PickDeviceStrings.title)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun pickDeviceStep_selectedCardExposesSelectedStateAndMergesTitle() {
        composeTestRule.setContent {
            MeAppTheme {
                PickDeviceStep(
                    deviceControl = FormControl.create(
                        PickDeviceStrings.Devices.WEIGHT_SCALE,
                        listOf(FormValidations.required()),
                    ),
                )
            }
        }

        // The merged card node carries the device title and the selected state.
        composeTestRule.onNodeWithText(PickDeviceStrings.weightScaleTitle)
            .assertIsSelected()
    }

    @Test
    fun deviceReadyStep_titleIsAHeading() {
        composeTestRule.setContent {
            MeAppTheme {
                DeviceReadyStep(
                    registeredDevices = setOf(ProductType.MY_WEIGHT),
                    onFinish = {},
                    onConnectAnother = {},
                )
            }
        }

        val title = DeviceReadyStrings.readyTitle(setOf(ProductType.MY_WEIGHT))
        composeTestRule.onNodeWithText(title)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun signupErrorStep_titleIsAHeading() {
        composeTestRule.setContent {
            MeAppTheme {
                SignupErrorStep(
                    failedDeviceId = ProductType.BABY.id,
                    registeredDevices = setOf(ProductType.BLOOD_PRESSURE),
                    onFinish = {},
                    onTryAgain = {},
                )
            }
        }

        composeTestRule.onNodeWithText(com.dmdbrands.gurus.weight.features.signup.strings.SignupErrorStrings.title)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }
}
