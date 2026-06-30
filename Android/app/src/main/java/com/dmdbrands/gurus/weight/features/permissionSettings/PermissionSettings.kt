package com.dmdbrands.gurus.weight.features.permissionSettings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.appPermissions.helper.PermissionGroup
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.PermissionItem
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Reusable composable for displaying permission groups with items that can be clicked to request permissions.
 *
 * @param permissionGroups List of permission groups to display
 * @param onRequestPermission Callback invoked when a permission item is clicked
 * @param modifier Modifier for the root composable
 * @param requiredPermissions Set of permission types that are required (for App permissions context)
 */
@Composable
fun PermissionSettings(
    permissionGroups: List<PermissionGroup>,
    onRequestPermission: (String) -> Unit,
    modifier: Modifier = Modifier,
    requiredPermissions: Set<String> = emptySet(),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        permissionGroups.forEach { group ->
            // Group header (section title)
            AppText(
                text = group.header,
                textType = TextType.ListTitle1,
                // TalkBack: expose the permission group title as a navigable heading.
                modifier = Modifier
                    .semantics { heading() }
                    .padding(bottom = MeTheme.spacing.sm),
            )
            Column(
                modifier = Modifier
                    .clip(shape = RoundedCornerShape(MeTheme.borderRadius.md))
                    .background(color = MeTheme.colorScheme.primaryBackground),
            ) {
                group.items.forEachIndexed { index, item ->
                    PermissionItem(
                        item = item,
                        onClick = { onRequestPermission(item.key) },
                        isRequired = requiredPermissions.contains(item.key)
                    )
                    if (index < group.items.size - 1) {
                        HorizontalDivider(color = MeTheme.colorScheme.utility, thickness = 0.5.dp)
                    }
                }
            }
            Spacer(modifier = Modifier.padding(MeTheme.spacing.sm))
        }
    }
}

@PreviewTheme
@Composable
fun PermissionSettingsPreview() {
  MeAppTheme {
    PermissionSettings(
      permissionGroups = emptyList(),
      onRequestPermission = {},
    )
  }
}

