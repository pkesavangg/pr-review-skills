package com.dmdbrands.gurus.weight.features.permissionSettings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.dmdbrands.gurus.weight.features.appPermissions.helper.PermissionGroup
import com.dmdbrands.gurus.weight.features.appPermissions.helper.PermissionItem
import com.dmdbrands.gurus.weight.features.common.components.PermissionItemStatus
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI test for the Permission Settings screen's TalkBack semantics
 * (MOB-857 — Phase 7). Verifies the permission group header is exposed as a navigable
 * heading. Requires a device/emulator (real Compose test rule).
 */
class PermissionSettingsAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun permissionGroupHeader_isAHeading() {
        val header = "Device permissions"
        val group = PermissionGroup(
            header = header,
            items = listOf(
                PermissionItem(
                    key = "bluetooth",
                    status = PermissionItemStatus.Granted,
                    enabledDescription = "Bluetooth is on",
                    disabledDescription = "Bluetooth is off",
                    group = header,
                ),
            ),
        )

        composeTestRule.setContent {
            MeAppTheme {
                PermissionSettings(
                    permissionGroups = listOf(group),
                    onRequestPermission = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(header)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }
}
