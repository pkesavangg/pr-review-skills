package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * Permission status enum for determining icon display
 */
enum class PermissionItemStatus {
    Granted,
    Denied,
    NotRequested
}

/**
 * A reusable permission item composable that displays a checkbox toggle with custom content.
 * Based on the Angular permissions-item component pattern.
 *
 * @param checked Whether the permission is granted/checked
 * @param onCheckedChange Callback when the checked state changes
 * @param permissionStatus Current status of the permission for icon display
 * @param modifier Modifier for the composable
 * @param enabled Whether the item is enabled for interaction
 * @param required Whether this permission is required
 * @param isButton Whether the item should behave as a button when unchecked
 * @param showValidation Whether to show validation states
 * @param content The content to display alongside the checkbox
 */
@Composable
fun PermissionItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    permissionStatus: PermissionItemStatus,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    required: Boolean = false,
    isButton: Boolean = true,
    showValidation: Boolean = true,
    content: @Composable () -> Unit,
    onClick: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var isDebouncing by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val isClickable = enabled && !isDebouncing
    val contentAlpha = if (enabled) 1f else 0.38f

    // Helper function to get icon based on permission status
    fun getPermissionIcon(): Int {
        return when (permissionStatus) {
            PermissionItemStatus.Granted -> AppIcons.Outlined.CheckedCircle
            PermissionItemStatus.Denied -> AppIcons.Outlined.MinusCircle
            PermissionItemStatus.NotRequested -> AppIcons.Outlined.MinusCircle
        }
    }

    // Helper function to get icon color based on permission status
    fun getPermissionIconType(): AppIconType {
        return when (permissionStatus) {
            PermissionItemStatus.Granted -> AppIconType.Primary
            PermissionItemStatus.Denied -> AppIconType.Danger
            PermissionItemStatus.NotRequested -> AppIconType.Tertiary
        }
    }
        Column (
            verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.xs),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MeTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                // Permission status icon (check/minus/unselected based on status)
                AppIcon(
                    id = getPermissionIcon(),
                    contentDescription = "Permission status",
                    type = getPermissionIconType()
                )
                Spacer(modifier = Modifier.width(MeTheme.spacing.sm))
               content()
                // This spacer will push the caret icon to the end
                Spacer(modifier = Modifier.weight(1f))
                // Forward arrow (similar to detail arrow in ion-item)
                AppIcon(
                    id = AppIcons.Default.RightCaret,
                    contentDescription = "Action",
                )
            }
        }
}

@PreviewTheme
@Composable
fun PermissionItemPreview() {
    MeAppTheme {
        Column(
            modifier = Modifier.padding(MeTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
        ) {
            // Granted permission
            PermissionItem(
                checked = true,
                onCheckedChange = { },
                permissionStatus = PermissionItemStatus.Granted,
                enabled = true,
                required = true,
                content = {
                    Column {
                        AppText(
                            text = "Camera Permission",
                            textType = TextType.Title,
                        )
                    }
                }
            ) {}

            // Denied permission
            PermissionItem(
                checked = false,
                onCheckedChange = { },
                permissionStatus = PermissionItemStatus.Denied,
                enabled = true,
                required = false,
                content = {
                    Column {
                        AppText(
                            text = "Location Permission",
                            textType = TextType.Title,
                        )
                        AppText(
                            text = "Detect nearby compatible devices",
                            textType = TextType.Body,
                        )
                    }
                }
            ) {}

            // Not requested permission
            PermissionItem(
                checked = false,
                onCheckedChange = { },
                permissionStatus = PermissionItemStatus.NotRequested,
                enabled = true,
                required = true,
                content = {
                    Column {
                        AppText(
                            text = "Bluetooth Permission",
                            textType = TextType.Title,
                        )
                        AppText(
                            text = "Connect to smart scales and health devices",
                            textType = TextType.Body,
                        )
                    }
                }
            )
        }
    }
}
