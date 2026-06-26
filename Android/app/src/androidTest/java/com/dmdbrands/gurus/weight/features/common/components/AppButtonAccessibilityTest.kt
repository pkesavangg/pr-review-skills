package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests verifying [AppButton] exposes the TalkBack Button role
 * (MOB-850 — Phase 0 shared-component accessibility).
 *
 * Both visual branches are covered:
 *  - transparent/text styles render via a clickable Box (needs explicit Role.Button + merge),
 *  - filled/outlined styles render via the Material3 Button (Role.Button is built in).
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class AppButtonAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun hasButtonRole() =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun transparentTextButton_exposesButtonRoleWithLabel() {
        composeTestRule.setContent {
            MeAppTheme {
                AppButton(label = "Cancel", type = ButtonType.TextPrimary, onClick = {})
            }
        }

        // Label is uppercased by the default TextTransform. The merged node carries both
        // the label and the Button role, which is exactly what TalkBack announces.
        composeTestRule.onNodeWithText("CANCEL").assert(hasButtonRole())
    }

    @Test
    fun filledButton_exposesButtonRoleWithLabel() {
        composeTestRule.setContent {
            MeAppTheme {
                AppButton(label = "Save", type = ButtonType.PrimaryFilled, onClick = {})
            }
        }

        composeTestRule.onNodeWithText("SAVE").assert(hasButtonRole())
    }
}
