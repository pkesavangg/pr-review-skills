package com.dmdbrands.gurus.weight.features.manualEntry.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.manualEntry.strings.EntryScreenStrings
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.EntryState
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests verifying the Manual Entry weight field renders the
 * unit suffix at the right edge of the field (MOB-1171).
 *
 * Canonical rule: the field label sits on the left and the unit suffix (`(lbs)` / `(kg)`)
 * is pinned to the right edge via [com.dmdbrands.gurus.weight.features.common.components.AppInput]'s
 * `trailingText` slot — the unit must NOT be baked into the label string.
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class WeightEntrySectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun weightField_imperial_rendersUnitAsRightEdgeSuffixNotInLabel() {
        composeTestRule.setContent {
            MeAppTheme {
                WeightEntrySection(state = EntryState(weightMode = WeightUnit.LB))
            }
        }

        // Unit is rendered as a parenthesised trailing suffix at the field's right edge.
        composeTestRule.onNodeWithText("(${WeightUnit.LB.label})").assertIsDisplayed()
        // The label is just "weight" — the unit is no longer baked into the label text.
        composeTestRule.onNodeWithText(EntryScreenStrings.WEIGHT_LABEL).assertIsDisplayed()
        composeTestRule
            .onNodeWithText("${EntryScreenStrings.WEIGHT_LABEL} (${WeightUnit.LB.label})")
            .assertDoesNotExist()
    }

    @Test
    fun weightField_metric_rendersKgSuffixNotInLabel() {
        composeTestRule.setContent {
            MeAppTheme {
                WeightEntrySection(state = EntryState(weightMode = WeightUnit.KG))
            }
        }

        composeTestRule.onNodeWithText("(${WeightUnit.KG.label})").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("${EntryScreenStrings.WEIGHT_LABEL} (${WeightUnit.KG.label})")
            .assertDoesNotExist()
    }
}
