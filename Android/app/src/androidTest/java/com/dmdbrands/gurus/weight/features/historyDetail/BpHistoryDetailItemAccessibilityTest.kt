package com.dmdbrands.gurus.weight.features.historyDetail

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BpmEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.BpmEntry
import com.dmdbrands.gurus.weight.features.historyDetail.components.BpHistoryDetailItem
import com.dmdbrands.gurus.weight.features.historyDetail.strings.HistoryDetailScreenStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the History detail BP row's TalkBack semantics
 * (MOB-854 — Phase 4, History). Requires a device/emulator.
 */
class BpHistoryDetailItemAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun bpmEntry() = BpmEntry(
        entry = EntryEntity(
            id = 1,
            accountId = "acct",
            entryTimestamp = "2025-06-19T06:30:00.000Z",
            serverTimestamp = "2025-06-19T10:29:13.914Z",
            opTimestamp = null,
            operationType = "create",
            deviceType = "bpm",
            deviceId = "manual",
            attempts = 0,
            unit = WeightUnit.LB,
            isSynced = true,
        ),
        bpmEntry = BpmEntryEntity(
            id = 1,
            systolic = 120,
            diastolic = 80,
            pulse = 60,
            meanArterial = "93",
            note = null,
        ),
    )

    @Test
    fun bpDetailRow_announcesPressurePulseAndCollapsedState() {
        composeTestRule.setContent {
            MeAppTheme {
                BpHistoryDetailItem(
                    entry = bpmEntry(),
                    dateDisplay = "Jun 19",
                    timeDisplay = "6:30 AM",
                    isExpanded = false,
                    onToggle = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(
                "Jun 19, 6:30 AM, " +
                    "${HistoryDetailScreenStrings.accPressureLabel} 120 " +
                    "${HistoryDetailScreenStrings.accOver} 80, " +
                    "${HistoryDetailScreenStrings.accPulseLabel} 60",
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    HistoryDetailScreenStrings.accCollapsedState,
                ),
            )
    }

    @Test
    fun bpDetailRow_announcesExpandedStateWhenOpen() {
        composeTestRule.setContent {
            MeAppTheme {
                BpHistoryDetailItem(
                    entry = bpmEntry(),
                    dateDisplay = "Jun 19",
                    timeDisplay = "6:30 AM",
                    isExpanded = true,
                    onToggle = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(
                "Jun 19, 6:30 AM, " +
                    "${HistoryDetailScreenStrings.accPressureLabel} 120 " +
                    "${HistoryDetailScreenStrings.accOver} 80, " +
                    "${HistoryDetailScreenStrings.accPulseLabel} 60",
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    HistoryDetailScreenStrings.accExpandedState,
                ),
            )
    }
}
