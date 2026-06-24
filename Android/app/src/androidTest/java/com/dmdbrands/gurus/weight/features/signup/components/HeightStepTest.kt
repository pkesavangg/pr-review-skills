package com.dmdbrands.gurus.weight.features.signup.components

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.dmdbrands.gurus.weight.features.common.components.HeightInput
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for [HeightStep] — covers the empty vs selected
 * branch and the dropdown-trigger accessibility semantics (MOB-258).
 * Requires a device/emulator (real Compose test rule).
 */
class HeightStepTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(height: HeightInput) {
        composeTestRule.setContent {
            MeAppTheme {
                HeightStep(
                    heightControl = FormControl.create(height, emptyList()),
                    useMetricControl = FormControl.create(false, emptyList()),
                    onMetricToggle = {},
                )
            }
        }
    }

    private fun dropdownTrigger() =
        composeTestRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList),
        )

    @Test
    fun selectedHeight_showsValueAndExposesItAsStateDescription() {
        val height = HeightInput.FtIn(feet = 7, inches = 1)
        setContent(height)

        composeTestRule.onNodeWithText(height.getString()).assertIsDisplayed()
        dropdownTrigger().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, height.getString()),
        )
    }

    @Test
    fun emptyHeight_isStillADropdownButHasNoSelectedValue() {
        val empty = HeightInput.FtIn(feet = 0, inches = 0)
        setContent(empty)

        // The trigger keeps its dropdown role even when empty…
        dropdownTrigger().assertExists()
        // …but exposes no selected value and renders no value text.
        dropdownTrigger().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        composeTestRule.onNodeWithText(empty.getString()).assertDoesNotExist()
    }
}
