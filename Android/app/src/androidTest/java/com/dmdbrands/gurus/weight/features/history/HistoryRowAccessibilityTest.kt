package com.dmdbrands.gurus.weight.features.history

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.dmdbrands.gurus.weight.domain.model.common.BabyWeekHistory
import com.dmdbrands.gurus.weight.domain.model.common.BpHistoryMonth
import com.dmdbrands.gurus.weight.domain.model.common.HistoryMonth
import com.dmdbrands.gurus.weight.features.history.components.BabyHistoryItem
import com.dmdbrands.gurus.weight.features.history.components.BpHistoryItem
import com.dmdbrands.gurus.weight.features.history.components.WeightHistoryItem
import com.dmdbrands.gurus.weight.features.history.strings.HistoryItemStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the History list rows' TalkBack semantics
 * (MOB-854 — Phase 4, History).
 *
 * Each row is merged into a single focusable node whose content description is a
 * coherent month + value announcement. Requires a device/emulator.
 */
class HistoryRowAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun weightHistoryItem_readsMonthEntriesAverageAndChangeAsOneNode() {
        composeTestRule.setContent {
            MeAppTheme {
                WeightHistoryItem(
                    item = HistoryMonth(
                        entryTimestamp = "Dec 2022",
                        entryCount = 3,
                        avgWeight = 148.4,
                        change = -1.4,
                    ).also {
                        it.unit = "lbs"
                        it.avgWeightPrefix = ""
                    },
                    onClick = {},
                )
            }
        }

        // One merged node carrying month, entry count, average and change.
        composeTestRule
            .onNodeWithContentDescription(
                "Dec 2022, 3 ${HistoryItemStrings.accEntriesSuffix}, " +
                    "${HistoryItemStrings.accAverageLabel} 148.4 lbs, " +
                    "${HistoryItemStrings.accChangeLabel} -1.4 lbs",
            )
            .assertExists()
    }

    @Test
    fun bpHistoryItem_announcesSystolicDiastolicAndPulse() {
        composeTestRule.setContent {
            MeAppTheme {
                BpHistoryItem(
                    item = BpHistoryMonth(
                        entryTimestamp = "Dec 2025",
                        avgSystolic = 115,
                        avgDiastolic = 75,
                        avgPulse = 60,
                        entryCount = 5,
                    ),
                    onClick = {},
                )
            }
        }

        // "/" is spoken as "over" so systolic/diastolic is unambiguous.
        composeTestRule
            .onNodeWithContentDescription(
                "Dec 2025, 5 ${HistoryItemStrings.accEntriesSuffix}, " +
                    "${HistoryItemStrings.accAvgPressureLabel} 115 ${HistoryItemStrings.accOver} 75, " +
                    "${HistoryItemStrings.accAvgPulseLabel} 60",
            )
            .assertExists()
    }

    @Test
    fun babyHistoryItem_readsDateWeightLengthAndPercentileAsOneNode() {
        composeTestRule.setContent {
            MeAppTheme {
                BabyHistoryItem(
                    item = BabyWeekHistory(
                        date = "Week 3",
                        dateKey = "2026-06-15",
                        entryCount = 2,
                        weightDecigrams = 40256, // 8 lb 14.0 oz in LB_OZ display
                        lengthMillimeters = 305, // 12 in
                        percentile = 6,
                    ),
                    onClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(
                "Week 3, 2 ${HistoryItemStrings.accEntriesSuffix}, " +
                    "${HistoryItemStrings.accWeightLabel} 8 lb 14.0 oz, " +
                    "${HistoryItemStrings.accLengthLabel} 12 in, " +
                    "${HistoryItemStrings.accPercentileLabel} 6 th",
            )
            .assertExists()
    }

    @Test
    fun historyRow_isExposedAsButtonAndFoldsOutTheChevronLabel() {
        composeTestRule.setContent {
            MeAppTheme {
                WeightHistoryItem(
                    item = HistoryMonth(
                        entryTimestamp = "Dec 2022",
                        entryCount = 3,
                        avgWeight = 148.4,
                        change = -1.4,
                    ).also {
                        it.unit = "lbs"
                        it.avgWeightPrefix = ""
                    },
                    onClick = {},
                )
            }
        }

        // The row performs the month-view navigation, so it is announced as a button.
        composeTestRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertExists()

        // The decorative chevron's label is folded out of the merged announcement, so it is
        // not a separately addressable node (the row's own summary conveys the navigation).
        composeTestRule
            .onNodeWithContentDescription(HistoryItemStrings.GoToMonthView)
            .assertDoesNotExist()
    }
}
