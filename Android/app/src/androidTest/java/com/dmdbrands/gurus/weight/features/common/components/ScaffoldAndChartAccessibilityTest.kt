package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.dmdbrands.gurus.weight.features.common.components.chart.ChartHeader
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the scaffolding/chart components' TalkBack semantics
 * (MOB-850 — Phase 0, Batch G).
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class ScaffoldAndChartAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appBar_titleIsAHeadingAndReadsRealTitle() {
        composeTestRule.setContent {
            MeAppTheme {
                AppBar(title = "Settings")
            }
        }

        // The real title is the spoken name (not the old fixed "AppBarTitle" override)...
        composeTestRule.onNodeWithText("Settings")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        // ...and the old override constant is gone.
        composeTestRule.onNodeWithContentDescription("AppBarTitle").assertDoesNotExist()
    }

    @Test
    fun chartHeader_groupsValueAndRangeIntoOneNode() {
        composeTestRule.setContent {
            MeAppTheme {
                ChartHeader(rangeData = "This Week") {
                    Text("180 lbs")
                }
            }
        }

        // mergeDescendants makes a single node carry both the value and the (lowercased) range.
        composeTestRule.onNode(
            hasText("180 lbs", substring = true).and(hasText("this week", substring = true)),
        ).assertExists()
    }
}
