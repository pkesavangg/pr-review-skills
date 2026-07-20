package com.dmdbrands.gurus.weight.features.DeviceUsers.components

import com.dmdbrands.gurus.weight.features.common.components.dismissKeyboardOnTap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.BtWifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.DeviceUsers.strings.DeviceUsersStrings
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.borderRadius
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.dmdbrands.library.ggbluetooth.model.GGBTUser

@Composable
fun DeviceUserList(
  modifier: Modifier = Modifier,
  userList: List<GGBTUser> = emptyList(),
  title: String? = null,
  subtitle: String? = null,
  userFormControl: FormControl<String>? = null,
  onDeleteUser: (GGBTUser) -> Unit,
) {
  val focusManager = LocalFocusManager.current

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .dismissKeyboardOnTap(),
  ) {
    DeviceUserListHeader(title = title, subtitle = subtitle)

    if (userFormControl != null) {
      AppInput(
        formControl = userFormControl,
        label = BtWifiScaleSetupStrings.UserList.UsernameLabel,
        type = AppInputType.TEXT,
        imeAction = ImeAction.Done,
        onImeAction = {
          focusManager.clearFocus()
        },
        modifier = Modifier.fillMaxWidth(),
      )
    }

    if (userList.isEmpty()) {
      EmptyDeviceUserList()
    } else {
      PopulatedDeviceUserList(
        userList = userList,
        showHeader = userFormControl != null,
        onDeleteUser = onDeleteUser,
      )
    }
  }
}

@Composable
private fun DeviceUserListHeader(
  title: String?,
  subtitle: String?,
) {
  title?.let {
    AppText(
      text = it,
      textType = TextType.ListTitle2,
      modifier = Modifier.padding(bottom = spacing.xs),
    )
  }
  subtitle?.let {
    AppText(
      text = it,
      textType = TextType.Body,
      modifier = Modifier.padding(bottom = spacing.lg),
    )
  }
}

@Composable
private fun EmptyDeviceUserList() {
  Column(
    Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    AppText(
      text = DeviceUsersStrings.NoUsers,
      textType = TextType.Body,
      modifier = Modifier.padding(vertical = spacing.x3l),
    )
  }
}

@Composable
private fun PopulatedDeviceUserList(
  userList: List<GGBTUser>,
  showHeader: Boolean,
  onDeleteUser: (GGBTUser) -> Unit,
) {
  Column {
    if (showHeader) {
      Row {
        AppText(
          text = DeviceUsersStrings.OtherUsers,
          textType = TextType.ListTitle1,
          modifier = Modifier.padding(bottom = spacing.md),
        )
        Spacer(modifier = Modifier.weight(1f))
        AppText(
          text = DeviceUsersStrings.MaxUsers,
          textType = TextType.ListSubtitle,
          modifier = Modifier.padding(bottom = spacing.md),
        )
      }
    }
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(borderRadius.sm)),
    ) {
      userList.forEach { user ->
        DeviceUserItem(
          user = user,
          onDeleteUser = onDeleteUser,
        )
        if (userList.size > 1 && userList.indexOf(user) < userList.size - 1) {
          HorizontalDivider(
            color = colorScheme.utility,
            thickness = .5.dp,
          )
        }
      }
    }
  }
}

@PreviewTheme()
@Composable
fun DeviceUserListPreview() {
  MeAppTheme {
    DeviceUserList(
      title = BtWifiScaleSetupStrings.UserList.Title,
      subtitle = BtWifiScaleSetupStrings.UserList.Subtitle,
      userList = listOf(
        GGBTUser(
          name = "Poongs",
          token = "424443432323424324",
          lastActive = 32332,
          isBodyMetricsEnabled = true,
        ),
        GGBTUser(
          name = "Poongs",
          token = "424443432323424324",
          lastActive = 32332,
          isBodyMetricsEnabled = true,
        ),
        GGBTUser(
          name = "Poongs",
          token = "424443432323424324",
          lastActive = 32332,
          isBodyMetricsEnabled = true,
        ),
        GGBTUser(
          name = "Poongs",
          token = "424443432323424324",
          lastActive = 32332,
          isBodyMetricsEnabled = true,
        ),
      ),
      onDeleteUser = {},
      modifier = Modifier.padding(
        horizontal = spacing.sm, vertical = spacing.md,
      ),
    )
  }
}
