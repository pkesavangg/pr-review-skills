package com.dmdbrands.gurus.weight.features.appPermissions

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.dmdbrands.gurus.weight.features.appPermissions.helper.PermissionGroup
import com.dmdbrands.gurus.weight.features.appPermissions.strings.AppPermissionsScreenStrings
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.permissionSettings.PermissionSettings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI tests for the App Permissions screen TalkBack semantics
 * (MOB-858 — Phase 8, Settings/account/scale management).
 *
 * Requires a device/emulator because it uses the real Compose test rule.
 */
class AppPermissionsAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun permissionGroupHeader_isAHeading() {
        composeTestRule.setContent {
            MeAppTheme {
                PermissionSettings(
                    permissionGroups = listOf(
                        PermissionGroup(
                            header = AppPermissionsScreenStrings.BluetoothHeader,
                            items = emptyList(),
                        ),
                    ),
                    onRequestPermission = {},
                )
            }
        }

        composeTestRule.onNodeWithText(AppPermissionsScreenStrings.BluetoothHeader)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun closeButton_hasMeaningfulContentDescription() {
        composeTestRule.setContent {
            MeAppTheme {
                AppScaffold(
                    title = AppPermissionsScreenStrings.Title,
                    navigationIcon = {
                        AppIconButton(
                            AppIcons.Default.Close,
                            contentDescription = AppPermissionsScreenStrings.accCloseLabel,
                        ) {}
                    },
                ) {}
            }
        }

        composeTestRule.onNodeWithContentDescription(AppPermissionsScreenStrings.accCloseLabel)
            .assertExists()
    }
}
