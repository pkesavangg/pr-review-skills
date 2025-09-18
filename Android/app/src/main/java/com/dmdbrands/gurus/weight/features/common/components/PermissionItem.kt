package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.dmdbrands.gurus.weight.features.appPermissions.helper.PermissionItem as PermissionItemData
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * Permission status enum for determining icon display
 */
enum class PermissionItemStatus {
  Granted,
  Denied,
  NotRequested
}

/**
 * A reusable permission item composable that displays a permission with a title and status/description.
 *
 * @param item The PermissionItem data class containing all relevant info
 * @param onClick Callback when the item is clicked
 * @param isRequired Whether this permission is required (affects color for unauthorized permissions)
 */
@Composable
fun PermissionItem(
  item: PermissionItemData,
  onClick: () -> Unit,
  isRequired: Boolean = false
) {
  // Helper function to get icon based on permission status
  fun getPermissionIcon(): Int {
    return when (item.status) {
      PermissionItemStatus.Granted -> AppIcons.Outlined.CheckedCircle
      PermissionItemStatus.Denied -> AppIcons.Outlined.MinusCircle
      PermissionItemStatus.NotRequested -> AppIcons.Outlined.MinusCircle
    }
  }

  // Helper function to get icon color based on permission status and requirement
  fun getPermissionIconType(): AppIconType {
    return when (item.status) {
      PermissionItemStatus.Granted -> AppIconType.Primary
      PermissionItemStatus.Denied, PermissionItemStatus.NotRequested -> {
        // For unauthorized permissions, show red if required, grey if not required
        if (isRequired) AppIconType.Danger else AppIconType.Tertiary
      }
    }
  }

  val isGranted = item.status == PermissionItemStatus.Granted
  val isDisabled = item.isDisabled
  val statusText = if (isGranted) item.enabledDescription else item.disabledDescription


  Row(
    modifier = Modifier
      .fillMaxWidth()
      .alpha(if (isDisabled) 0.5f else 1.0f)
      .clickable(enabled = isDisabled || !isGranted, onClick = onClick)
      .padding(MeTheme.spacing.sm),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(modifier = Modifier.weight(1f)) {
      // Permission status icon (check/minus/unselected based on status)
      AppIcon(
        id = getPermissionIcon(),
        contentDescription = "Permission status",
        type = getPermissionIconType(),
      )
      Spacer(modifier = Modifier.width(MeTheme.spacing.sm))
      if (!statusText.isNullOrBlank()) {
        AppText(
          text = statusText,
          textType = TextType.Body,
        )
      }
    }
    if(!isGranted) {
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
        item = PermissionItemData(
          key = "camera",
          status = PermissionItemStatus.Granted,
          enabledDescription = "Camera authorized",
          disabledDescription = "Allow camera access",
          group = "Camera (AppSync)",
        ),
        onClick = {},
      )
      // Denied permission (required - red)
      PermissionItem(
        item = PermissionItemData(
          key = "location",
          status = PermissionItemStatus.Denied,
          enabledDescription = "Location authorized",
          disabledDescription = "Allow location access",
          group = "Location",
        ),
        onClick = {},
        isRequired = true
      )
      // Not requested permission (not required - grey)
      PermissionItem(
        item = PermissionItemData(
          key = "bluetooth",
          status = PermissionItemStatus.NotRequested,
          enabledDescription = "Bluetooth authorized",
          disabledDescription = "Allow Bluetooth access",
          group = "Bluetooth",
        ),
        onClick = {},
        isRequired = false
      )
    }
  }
}
