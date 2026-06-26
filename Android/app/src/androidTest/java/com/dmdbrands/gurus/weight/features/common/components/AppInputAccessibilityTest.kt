package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormValidations
import com.dmdbrands.gurus.weight.features.common.strings.AppInputStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests verifying [AppInput] TalkBack semantics
 * (MOB-850 — Phase 0 shared-component accessibility):
 *  - trailing icon labels come from [AppInputStrings] (no hardcoded literals),
 *  - the password toggle label flips between Show/Hide,
 *  - the validation error message is exposed on the field's accessibility node.
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class AppInputAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun clearTrailingIcon_usesAccLabel() {
        val control = FormControl.create("abc", emptyList())
        composeTestRule.setContent {
            MeAppTheme {
                AppInput(formControl = control, label = "Name", type = AppInputType.TEXT)
            }
        }

        composeTestRule
            .onNodeWithContentDescription(AppInputStrings.accClearLabel)
            .assertIsDisplayed()
    }

    @Test
    fun passwordToggle_flipsBetweenShowAndHideLabels() {
        val control = FormControl.create("secret", emptyList())
        composeTestRule.setContent {
            MeAppTheme {
                AppInput(formControl = control, label = "Password", type = AppInputType.PASSWORD)
            }
        }

        // Hidden by default -> offers to show.
        composeTestRule
            .onNodeWithContentDescription(AppInputStrings.accShowPasswordLabel)
            .assertIsDisplayed()
            .performClick()

        // After toggling, it offers to hide.
        composeTestRule
            .onNodeWithContentDescription(AppInputStrings.accHidePasswordLabel)
            .assertIsDisplayed()
    }

    @Test
    fun validationError_isExposedOnFieldSemantics() {
        val control = FormControl.create("", listOf(FormValidations.required()))
        control.markAsTouched()
        control.validate()
        val expectedMessage = control.error?.message.orEmpty()
        assertThat(expectedMessage).isNotEmpty()

        composeTestRule.setContent {
            MeAppTheme {
                AppInput(formControl = control, label = "Name", type = AppInputType.TEXT)
            }
        }

        composeTestRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.Error, expectedMessage))
            .assertExists()
    }
}
