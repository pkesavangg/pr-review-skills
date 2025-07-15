package com.greatergoods.meapp.features.common.components

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
import com.greatergoods.meapp.features.appPermissions.helper.PermissionItem as PermissionItemData
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
 * A reusable permission item composable that displays a permission with a title and status/description.
 *
 * @param item The PermissionItem data class containing all relevant info
 * @param onClick Callback when the item is clicked
 */
@Composable
fun PermissionItem(
  item: PermissionItemData,
  onClick: () -> Unit
) {
  // Helper function to get icon based on permission status
  fun getPermissionIcon(): Int {
    return when (item.status) {
      PermissionItemStatus.Granted -> AppIcons.Outlined.CheckedCircle
      PermissionItemStatus.Denied -> AppIcons.Outlined.MinusCircle
      PermissionItemStatus.NotRequested -> AppIcons.Outlined.MinusCircle
    }
  }

  // Helper function to get icon color based on permission status
  fun getPermissionIconType(): AppIconType {
    return when (item.status) {
      PermissionItemStatus.Granted -> AppIconType.Primary
      PermissionItemStatus.Denied -> AppIconType.Danger
      PermissionItemStatus.NotRequested -> AppIconType.Tertiary
    }
  }

  val isGranted = item.status == PermissionItemStatus.Granted
  val disabled = isGranted
  val statusText = if (isGranted) item.enabledDescription else item.disabledDescription


  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(enabled = !disabled, onClick = onClick)
      .padding(MeTheme.spacing.md),
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
    AppIcon(
      id = AppIcons.Default.RightCaret,
      contentDescription = "Action",
    )
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
      ) {}
      // Denied permission
      PermissionItem(
        item = PermissionItemData(
          key = "location",
          status = PermissionItemStatus.Denied,
          enabledDescription = "Location authorized",
          disabledDescription = "Allow location access",
          group = "Location",
        ),
      ) {}
      // Not requested permission
      PermissionItem(
        item = PermissionItemData(
          key = "bluetooth",
          status = PermissionItemStatus.NotRequested,
          enabledDescription = "Bluetooth authorized",
          disabledDescription = "Allow Bluetooth access",
          group = "Bluetooth",
        ),
      ) {}
    }
  }
}
