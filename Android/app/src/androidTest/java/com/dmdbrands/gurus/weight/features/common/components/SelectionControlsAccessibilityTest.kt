package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the selection controls' TalkBack semantics
 * (MOB-850 — Phase 0 shared-component accessibility).
 *
 * AppToggle and AppRadioButton are NOT modified for the epic — the Material3 Switch
 * and Modifier.selectable already expose toggle/role/selected state. These tests pin
 * that down so the behaviour can't silently regress. AppChip, AppRadioGroup and
 * AppPicker received explicit selected/group semantics; those are verified here too.
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class SelectionControlsAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun hasRadioButtonRole() =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    @Test
    fun appToggle_exposesOnOffState() {
        composeTestRule.setContent {
            MeAppTheme {
                var checked by remember { mutableStateOf(true) }
                AppToggle(checked = checked, onCheckedChange = { checked = it })
            }
        }

        composeTestRule.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun appToggle_offState_isExposed() {
        composeTestRule.setContent {
            MeAppTheme {
                AppToggle(checked = false, onCheckedChange = {})
            }
        }

        composeTestRule.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun appChip_exposesSelectedStateWithLabel() {
        composeTestRule.setContent {
            MeAppTheme {
                AppChip(label = "Kg", selected = true, textTransform = TextTransform.NONE, onClick = {})
            }
        }

        composeTestRule.onNodeWithText("Kg").assertIsSelected()
    }

    @Test
    fun appRadioButton_exposesRoleAndSelectedWithLabel() {
        composeTestRule.setContent {
            MeAppTheme {
                AppRadioButton(selected = true, label = "Male", onClick = {})
            }
        }

        composeTestRule.onNodeWithText("Male")
            .assert(hasRadioButtonRole())
            .assertIsSelected()
    }

    @Test
    fun appRadioGroup_selectedOptionIsExposed() {
        composeTestRule.setContent {
            MeAppTheme {
                AppRadioGroup(
                    options = listOf(
                        RadioButtonOption(id = "a", label = "Option A"),
                        RadioButtonOption(id = "b", label = "Option B"),
                    ),
                    selectedItem = "b",
                    onOptionSelected = {},
                    groupLabel = "Choose",
                )
            }
        }

        composeTestRule.onNodeWithText("Option B").assertIsSelected()
        composeTestRule.onNodeWithText("Choose").assertExists()
    }

    @Test
    fun appPicker_centeredItemIsExposedAsSelected() {
        composeTestRule.setContent {
            MeAppTheme {
                AppPicker(
                    items = listOf("159 cm", "160 cm", "161 cm"),
                    selectedItem = "160 cm",
                    onItemSelected = {},
                )
            }
        }

        composeTestRule.onNodeWithText("160 cm").assertIsSelected()
    }
}
