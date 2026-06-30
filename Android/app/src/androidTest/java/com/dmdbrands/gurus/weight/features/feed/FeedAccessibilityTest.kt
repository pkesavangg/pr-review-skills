package com.dmdbrands.gurus.weight.features.feed

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.dmdbrands.gurus.weight.features.common.components.AppBar
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.feed.strings.FeedStrings
import com.dmdbrands.gurus.weight.features.feedMessages.strings.FeedMessagesStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the Feed & messages screens' TalkBack semantics
 * (MOB-859 — Phase 9).
 *
 * The feed/messages screens wrap IAM-package content and label their AppBar icon-only
 * buttons via *Strings consts. These tests assert the icon buttons are announced with a
 * meaningful contentDescription and the Button role, and that the AppBar title reads as a
 * heading (inherited from the shared AppBar in MOB-850).
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class FeedAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Composable
    private fun closeButton(contentDescription: String) {
        AppIconButton(
            AppIcons.Default.Close,
            contentDescription = contentDescription,
        ) {}
    }

    @Test
    fun feedLanding_closeButton_isLabeledButton() {
        composeTestRule.setContent {
            MeAppTheme { closeButton(FeedStrings.accCloseButton) }
        }

        composeTestRule.onNodeWithContentDescription(FeedStrings.accCloseButton)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun feedLanding_faqButton_isLabeledButton() {
        composeTestRule.setContent {
            MeAppTheme {
                AppIconButton(
                    AppIcons.Outlined.Help,
                    contentDescription = FeedStrings.accFaqButton,
                ) {}
            }
        }

        composeTestRule.onNodeWithContentDescription(FeedStrings.accFaqButton)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun feedMessages_closeButton_isLabeledButton() {
        composeTestRule.setContent {
            MeAppTheme { closeButton(FeedMessagesStrings.accCloseButton) }
        }

        composeTestRule.onNodeWithContentDescription(FeedMessagesStrings.accCloseButton)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun feedMessages_appBarTitle_isHeading() {
        composeTestRule.setContent {
            MeAppTheme {
                AppBar(title = FeedMessagesStrings.Title)
            }
        }

        composeTestRule.onNodeWithText(FeedMessagesStrings.Title)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }
}
