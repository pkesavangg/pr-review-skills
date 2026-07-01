package com.dmdbrands.gurus.weight.features.DeviceUsers.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.DeviceUsers.strings.DeviceUsersStrings
import com.dmdbrands.gurus.weight.features.common.components.AppIcon
import com.dmdbrands.gurus.weight.features.common.components.AppIconType
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.helper.StringUtil.formatTimestamp
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.dmdbrands.library.ggbluetooth.model.GGBTUser

@Composable
fun DeviceUserItem(
  user: GGBTUser,
  onDeleteUser: (GGBTUser) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(colorScheme.primaryBackground)
      .padding(MeTheme.spacing.sm),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
      // Weight only mode enabled
      if (!user.isBodyMetricsEnabled) {
        AppIcon(
          id = AppIcons.Default.WeightOnlyMode,
          contentDescription = DeviceUsersStrings.UserProfileIcon,
          modifier = Modifier.size(24.dp),
          type = AppIconType.Primary,
        )
      }

      // User Info
      Column {
        AppText(
          text = user.name,
          textType = TextType.Body,
        )
        AppText(
          text = DeviceUsersStrings.LastActiveOn(user.lastActive.formatTimestamp()).lowercase(),
          textType = TextType.SubHeading,
        )
      }
    }

    // Delete Button
    AppIcon(
      id = AppIcons.Default.Delete,
      contentDescription = DeviceUsersStrings.DeleteUser,
      modifier = Modifier.size(24.dp),
      type = AppIconType.Danger,
      onClick = { onDeleteUser(user) },
    )
  }
}

@PreviewTheme()
@Composable
fun DeviceUserItemPreview() {
  MeAppTheme {
    Column(
    ) {
      // Preview with body metrics enabled
      DeviceUserItem(
        user = GGBTUser(
          name = "Poongs 1",
          token = "424443432323424324",
          lastActive = 1656720000, // July 02, 2022
          isBodyMetricsEnabled = true,
        ),
        onDeleteUser = { _ -> },
      )

      // Preview with body metrics disabled
      DeviceUserItem(
        user = GGBTUser(
          name = "Poongs 2",
          token = "424443432323424324",
          lastActive = 1679875200, // March 30, 2023
          isBodyMetricsEnabled = false,
        ),
        onDeleteUser = { _ -> },
      )
    }
  }
}
