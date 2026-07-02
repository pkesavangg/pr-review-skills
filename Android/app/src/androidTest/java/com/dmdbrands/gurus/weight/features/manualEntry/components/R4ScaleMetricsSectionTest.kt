package com.dmdbrands.gurus.weight.features.manualEntry.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.manualEntry.strings.EntryScreenStrings
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.R4ScaleMetricsFormControls
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests verifying the R4/scale metrics section renders each field's
 * unit as a right-edge suffix via [com.dmdbrands.gurus.weight.features.common.components.AppInput]'s
 * `trailingText` slot rather than baking the unit into the label string (MOB-1171).
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class R4ScaleMetricsSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setSection() {
        composeTestRule.setContent {
            MeAppTheme {
                R4ScaleMetricsSection(
                    controls = R4ScaleMetricsFormControls(
                        heartRate = FormControl.create("", emptyList()),
                        boneMass = FormControl.create("", emptyList()),
                        visceralFat = FormControl.create("", emptyList()),
                        subcutaneousFat = FormControl.create("", emptyList()),
                        protein = FormControl.create("", emptyList()),
                        skeletalMuscles = FormControl.create("", emptyList()),
                        bmr = FormControl.create("", emptyList()),
                        metabolicAge = FormControl.create("", emptyList()),
                    ),
                )
            }
        }
    }

    @Test
    fun distinctUnits_renderAsRightEdgeSuffix() {
        setSection()

        // Unique units each render once as a parenthesised trailing suffix.
        composeTestRule.onNodeWithText("(${EntryScreenStrings.HEART_RATE_UNIT})").assertIsDisplayed()
        composeTestRule.onNodeWithText("(${EntryScreenStrings.VISCERAL_FAT_UNIT})").assertIsDisplayed()
        composeTestRule.onNodeWithText("(${EntryScreenStrings.BMR_UNIT})").assertIsDisplayed()
        composeTestRule.onNodeWithText("(${EntryScreenStrings.METABOLIC_AGE_UNIT})").assertIsDisplayed()
        // Bone mass, subcutaneous fat, protein and skeletal muscles all use "(%)".
        composeTestRule.onAllNodesWithText("(${EntryScreenStrings.BONE_MASS_UNIT})")
            .assertCountEquals(4)
    }

    @Test
    fun visceralFatUnit_preservesLvCasing() {
        setSection()

        // The unit casing is preserved as "Lv." (not lowercased) — MOB-1171 was position-only.
        composeTestRule.onNodeWithText("(Lv.)").assertIsDisplayed()
    }

    @Test
    fun labels_areBaseTextWithoutBakedInUnit() {
        setSection()

        composeTestRule.onNodeWithText(EntryScreenStrings.HEART_RATE_LABEL).assertIsDisplayed()
        composeTestRule
            .onNodeWithText("${EntryScreenStrings.HEART_RATE_LABEL} (${EntryScreenStrings.HEART_RATE_UNIT})")
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithText("${EntryScreenStrings.VISCERAL_FAT_LABEL} (${EntryScreenStrings.VISCERAL_FAT_UNIT})")
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithText("${EntryScreenStrings.BMR_LABEL} (${EntryScreenStrings.BMR_UNIT})")
            .assertDoesNotExist()
    }
}
