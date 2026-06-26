package com.dmdbrands.gurus.weight.features.manualEntry.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.manualEntry.strings.EntryScreenStrings
import com.dmdbrands.gurus.weight.features.manualEntry.viewmodel.GeneralMetricsFormControls
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests verifying [ExpandableMetricsCard] TalkBack semantics
 * (MOB-853 — Phase 3 Manual Entry accessibility):
 *  - the section title is exposed as a heading so users can navigate by heading,
 *  - the expand/collapse chevron label comes from [EntryScreenStrings] (no hardcoded
 *    literals) and flips between Expand and Collapse as the card toggles.
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class ExpandableMetricsCardAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun emptyGeneralMetrics() =
        GeneralMetricsFormControls(
            bodyMassIndex = FormControl.create("", emptyList()),
            bodyFat = FormControl.create("", emptyList()),
            muscleMass = FormControl.create("", emptyList()),
            bodyWater = FormControl.create("", emptyList()),
        )

    @Test
    fun sectionTitle_isAHeadingAndReadsRealTitle() {
        composeTestRule.setContent {
            MeAppTheme {
                ExpandableMetricsCard(
                    title = EntryScreenStrings.METRICS_SECTION_TITLE,
                    subheading = EntryScreenStrings.METRICS_SECTION_SUBHEADING,
                    generalMetrics = emptyGeneralMetrics(),
                    dashboardType = DashboardType.DASHBOARD_4_METRICS,
                )
            }
        }

        // The real title is the spoken name and is marked as a heading.
        composeTestRule.onNodeWithText(EntryScreenStrings.METRICS_SECTION_TITLE)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun chevron_flipsBetweenExpandAndCollapseLabels() {
        composeTestRule.setContent {
            MeAppTheme {
                ExpandableMetricsCard(
                    title = EntryScreenStrings.METRICS_SECTION_TITLE,
                    generalMetrics = emptyGeneralMetrics(),
                    expandedInitially = false,
                    dashboardType = DashboardType.DASHBOARD_4_METRICS,
                )
            }
        }

        // Collapsed by default -> offers to expand.
        composeTestRule
            .onNodeWithContentDescription(EntryScreenStrings.accMetricsExpandLabel)
            .assertIsDisplayed()
            .performClick()

        // After toggling, it offers to collapse.
        composeTestRule
            .onNodeWithContentDescription(EntryScreenStrings.accMetricsCollapseLabel)
            .assertIsDisplayed()
    }
}
