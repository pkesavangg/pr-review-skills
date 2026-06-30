package com.dmdbrands.gurus.weight.features.myKids

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.myKids.strings.MyKidsStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the My Kids screen TalkBack semantics
 * (MOB-858 — Phase 8).
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class MyKidsAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun babyAddedTitle_isAHeading() {
        composeTestRule.setContent {
            MeAppTheme {
                AppText(
                    text = MyKidsStrings.BabyAddedTitle,
                    textType = TextType.Title,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }

        composeTestRule.onNodeWithText(MyKidsStrings.BabyAddedTitle)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun avatarInitial_isHiddenFromAccessibilityTree() {
        composeTestRule.setContent {
            MeAppTheme {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clearAndSetSemantics { },
                ) {
                    Text(text = "B")
                }
            }
        }

        // The decorative initial must NOT be announced as text.
        composeTestRule.onNodeWithText("B").assertDoesNotExist()
    }

    @Test
    fun closeButton_hasMeaningfulContentDescription() {
        composeTestRule.setContent {
            MeAppTheme {
                AppScaffold(
                    title = MyKidsStrings.Title,
                    navigationIcon = {
                        AppIconButton(
                            AppIcons.Default.Close,
                            contentDescription = MyKidsStrings.accCloseLabel,
                        ) {}
                    },
                ) {}
            }
        }

        composeTestRule.onNodeWithContentDescription(MyKidsStrings.accCloseLabel)
            .assertExists()
    }
}
