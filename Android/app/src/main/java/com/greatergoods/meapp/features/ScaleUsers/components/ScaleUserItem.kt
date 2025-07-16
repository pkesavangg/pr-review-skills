package com.greatergoods.meapp.features.ScaleUsers.components

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
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.greatergoods.meapp.features.ScaleUsers.strings.ScaleUsersStrings
import com.greatergoods.meapp.features.common.components.AppIcon
import com.greatergoods.meapp.features.common.components.AppIconType
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.helper.StringUtil.formatTimestamp
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing

@Composable
fun ScaleUserItem(
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
      if (user.isBodyMetricsEnabled) {
        AppIcon(
          id = AppIcons.Default.WeightOnlyMode,
          contentDescription = ScaleUsersStrings.UserProfileIcon,
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
          text = ScaleUsersStrings.LastActiveOn(user.lastActive.formatTimestamp()).lowercase(),
          textType = TextType.SubHeading,
        )
      }
    }

    // Delete Button
    AppIcon(
      id = AppIcons.Default.Delete,
      contentDescription = ScaleUsersStrings.DeleteUser,
      modifier = Modifier.size(24.dp),
      type = AppIconType.Danger,
      onClick = { onDeleteUser(user) },
    )
  }
}

@PreviewTheme()
@Composable
fun ScaleUserItemPreview() {
  MeAppTheme {
    Column(
    ) {
      // Preview with body metrics enabled
      ScaleUserItem(
        user = GGBTUser(
          name = "Poongs 1",
          token = "424443432323424324",
          lastActive = 1656720000, // July 02, 2022
          isBodyMetricsEnabled = true,
        ),
        onDeleteUser = { _ -> },
      )

      // Preview with body metrics disabled
      ScaleUserItem(
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
