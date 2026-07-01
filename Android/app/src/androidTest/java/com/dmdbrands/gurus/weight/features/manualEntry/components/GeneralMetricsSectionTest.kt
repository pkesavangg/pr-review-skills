package com.dmdbrands.gurus.weight.features.manualEntry.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.manualEntry.strings.EntryScreenStrings
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.GeneralMetricsFormControls
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests verifying the General Metrics section renders each field's
 * unit as a right-edge suffix via [com.dmdbrands.gurus.weight.features.common.components.AppInput]'s
 * `trailingText` slot rather than baking the unit into the label string (MOB-1171).
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class GeneralMetricsSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setSection() {
        composeTestRule.setContent {
            MeAppTheme {
                GeneralMetricsSection(
                    controls = GeneralMetricsFormControls(
                        bodyMassIndex = FormControl.create("", emptyList()),
                        bodyFat = FormControl.create("", emptyList()),
                        muscleMass = FormControl.create("", emptyList()),
                        bodyWater = FormControl.create("", emptyList()),
                    ),
                    isDashboardType = DashboardType.DASHBOARD_4_METRICS,
                )
            }
        }
    }

    @Test
    fun percentUnits_renderAsRightEdgeSuffix_notInLabel() {
        setSection()

        // Body fat, muscle mass and body water each render "(%)" as a trailing suffix.
        composeTestRule.onAllNodesWithText("(${EntryScreenStrings.BODY_FAT_UNIT})")
            .assertCountEquals(3)
    }

    @Test
    fun labels_areBaseTextWithoutBakedInUnit() {
        setSection()

        // Labels are the base text; the old "label (unit)" form must no longer exist.
        composeTestRule.onNodeWithText(EntryScreenStrings.BODY_FAT_LABEL).assertIsDisplayed()
        composeTestRule
            .onNodeWithText("${EntryScreenStrings.BODY_FAT_LABEL} (${EntryScreenStrings.BODY_FAT_UNIT})")
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithText("${EntryScreenStrings.MUSCLE_MASS_LABEL} (${EntryScreenStrings.MUSCLE_MASS_UNIT})")
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithText("${EntryScreenStrings.BODY_WATER_LABEL} (${EntryScreenStrings.BODY_WATER_UNIT})")
            .assertDoesNotExist()
    }
}
