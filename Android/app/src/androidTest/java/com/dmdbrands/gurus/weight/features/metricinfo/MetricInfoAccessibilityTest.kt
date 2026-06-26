package com.dmdbrands.gurus.weight.features.metricinfo

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import com.dmdbrands.gurus.weight.features.metricinfo.components.MetricInfoInfoSection
import com.dmdbrands.gurus.weight.features.metricinfo.components.MetricInfoResourcesSection
import com.dmdbrands.gurus.weight.features.metricinfo.components.MetricInfoValueSection
import com.dmdbrands.gurus.weight.features.metricinfo.strings.MetricInfoStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the Metric Info screen's TalkBack semantics
 * (MOB-852 — Phase 2). Semantics-only assertions; no testTag dependencies.
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class MetricInfoAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun infoSection_titleIsAHeading() {
        composeTestRule.setContent {
            MeAppTheme {
                MetricInfoInfoSection(metricKey = MetricKey.WEIGHT)
            }
        }

        // "Why Weight?" is the section header — navigable by heading.
        composeTestRule.onNodeWithText("Why Weight?")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun resourcesSection_titleIsAHeading() {
        composeTestRule.setContent {
            MeAppTheme {
                MetricInfoResourcesSection(metricKey = MetricKey.WEIGHT) {}
            }
        }

        composeTestRule.onNodeWithText(MetricInfoStrings.ResourcesTitle)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun valueSection_mergesValueUnitAndDateIntoOneNode() {
        composeTestRule.setContent {
            MeAppTheme {
                MetricInfoValueSection(
                    value = "180",
                    unit = "lb",
                    subText = "week average",
                )
            }
        }

        // mergeDescendants produces a single node carrying value, unit and date.
        composeTestRule.onNode(
            hasText("180", substring = true)
                .and(hasText("lb", substring = true))
                .and(hasText("week average", substring = true)),
        ).assertExists()
    }
}
