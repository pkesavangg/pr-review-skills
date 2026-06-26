package com.dmdbrands.gurus.weight.features.dashboard.snapshot

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.dmdbrands.gurus.weight.features.dashboard.snapshot.components.SnapshotLineChart
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI test for the snapshot line chart's TalkBack summary
 * (MOB-852 — Phase 2). The chart is a custom Vico Canvas with no text nodes, so the
 * caller-supplied [SnapshotLineChart] contentDescription is the only thing TalkBack can read.
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class SnapshotLineChartAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chart_exposesCallerSuppliedSummaryAsContentDescription() {
        val summary = "Chart showing 180 lb week average"
        // The contentDescription is applied to the chart host's modifier unconditionally,
        // so the semantics node exists even before the producer streams any data.
        val producer = CartesianChartModelProducer()

        composeTestRule.setContent {
            MeAppTheme {
                SnapshotLineChart(
                    modelProducer = producer,
                    lineColor = Color.Blue,
                    startTimestamp = 1L,
                    endTimestamp = 3L,
                    yMin = 0.0,
                    yMax = 10.0,
                    chartContentDescription = summary,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(summary).assertExists()
    }
}
