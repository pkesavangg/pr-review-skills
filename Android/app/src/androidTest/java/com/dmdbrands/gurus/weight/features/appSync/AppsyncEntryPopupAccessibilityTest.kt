package com.dmdbrands.gurus.weight.features.appSync

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics
import com.dmdbrands.gurus.weight.features.appSync.strings.AppSyncStrings
import com.dmdbrands.gurus.weight.features.common.components.AppsyncEntryPopup
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the AppSync scanner result popup TalkBack semantics
 * (MOB-856 — Phase 6, AppSync scanner).
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class AppsyncEntryPopupAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun mockEntry() = ScaleEntry(
        entry = EntryEntity(
            id = 1L,
            accountId = "test-account-123",
            entryTimestamp = "0",
            serverTimestamp = null,
            opTimestamp = null,
            unit = WeightUnit.KG,
            operationType = "CREATE",
            deviceType = "appsync",
            deviceId = "appsync-scale-001",
            attempts = 0,
            isSynced = false,
        ),
        scale = ScaleEntryWithMetrics(
            scaleEntry = BodyScaleEntryEntity(
                id = 1L,
                weight = 85.1,
                bodyFat = 28.2,
                muscleMass = 31.9,
                water = 50.5,
                bmi = 35.5,
                source = "Appsync scale",
            ),
            scaleEntryMetric = null,
        ),
    )

    @Test
    fun appsyncEntryPopup_titleIsAHeading() {
        composeTestRule.setContent {
            MeAppTheme {
                AppsyncEntryPopup(entry = mockEntry(), onEdit = {}, onSave = {})
            }
        }

        composeTestRule.onNodeWithText(AppPopupStrings.AppsyncEntryPopup.Title)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun appsyncEntryPopup_announcesScanResultAsLiveRegion() {
        composeTestRule.setContent {
            MeAppTheme {
                AppsyncEntryPopup(entry = mockEntry(), onEdit = {}, onSave = {})
            }
        }

        // The polite live region carries an explicit "scan result" prefix so TalkBack
        // users hear that the scan succeeded, then the title.
        composeTestRule.onNodeWithContentDescription(
            "${AppSyncStrings.accScanResultLabel}: ${AppPopupStrings.AppsyncEntryPopup.Title}",
        ).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun appsyncEntryPopup_readsLabeledMetricValues() {
        composeTestRule.setContent {
            MeAppTheme {
                AppsyncEntryPopup(entry = mockEntry(), onEdit = {}, onSave = {})
            }
        }

        // Each metric row already announces its own label + value to TalkBack.
        composeTestRule.onNodeWithText(
            AppPopupStrings.AppsyncEntryPopup.Bodyfat(28.2f),
        ).assertExists()
        composeTestRule.onNodeWithText(
            AppPopupStrings.AppsyncEntryPopup.MuscleMass(31.9f),
        ).assertExists()
    }
}
