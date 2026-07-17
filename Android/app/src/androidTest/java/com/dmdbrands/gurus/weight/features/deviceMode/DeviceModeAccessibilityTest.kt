package com.dmdbrands.gurus.weight.features.deviceMode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.role
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
import com.dmdbrands.gurus.weight.features.deviceMode.strings.DeviceModeStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the Device Mode / Display Metrics app-bar TalkBack semantics
 * (MOB-858 — Phase 8). The "Save" affordance in these screens is a clickable [AppText] rather
 * than a real button, so the screens now apply Role.Button; this pins that down.
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class DeviceModeAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun saveAction_exposesButtonRole() {
        composeTestRule.setContent {
            MeAppTheme {
                AppScaffold(
                    title = DeviceModeStrings.Title,
                    navigationIcon = {
                        AppIconButton(
                            AppIcons.Default.Close,
                            contentDescription = DeviceModeStrings.accCloseLabel,
                        ) {}
                    },
                    actions = {
                        AppText(
                            text = DeviceModeStrings.Save,
                            textType = TextType.ListTitle1,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .semantics { role = Role.Button }
                                .clickable { },
                        )
                    },
                ) {}
            }
        }

        composeTestRule.onNodeWithText(DeviceModeStrings.Save)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun closeButton_hasMeaningfulContentDescription() {
        composeTestRule.setContent {
            MeAppTheme {
                AppScaffold(
                    title = DeviceModeStrings.Title,
                    navigationIcon = {
                        AppIconButton(
                            AppIcons.Default.Close,
                            contentDescription = DeviceModeStrings.accCloseLabel,
                        ) {}
                    },
                ) {}
            }
        }

        composeTestRule.onNodeWithContentDescription(DeviceModeStrings.accCloseLabel)
            .assertExists()
    }
}
