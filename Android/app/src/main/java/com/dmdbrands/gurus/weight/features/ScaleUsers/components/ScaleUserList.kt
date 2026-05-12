package com.dmdbrands.gurus.weight.features.ScaleUsers.components

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
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BtWifiScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleUsers.strings.ScaleUsersStrings
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
fun ScaleUserList(
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
    title?.let {
      AppText(
        text = title,
        textType = TextType.ListTitle2,
        modifier = Modifier.padding(bottom = spacing.xs),
      )
    }
    subtitle?.let {
      AppText(
        text = subtitle,
        textType = TextType.Body,
        modifier = Modifier.padding(bottom = spacing.lg),
      )
    }

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
      Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        AppText(
          text = ScaleUsersStrings.NoUsers,
          textType = TextType.Body,
          modifier = Modifier.padding(vertical = spacing.x3l),
        )
      }
    } else {
      Column {
        if (userFormControl != null) {
          Row {
            AppText(
              text = ScaleUsersStrings.OtherUsers,
              textType = TextType.ListTitle1,
              modifier = Modifier.padding(bottom = spacing.md),
            )
            Spacer(modifier = Modifier.weight(1f))
            AppText(
              text = ScaleUsersStrings.MaxUsers,
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
            ScaleUserItem(
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
  }
}

@PreviewTheme()
@Composable
fun ScaleUserListPreview() {
  MeAppTheme {
    ScaleUserList(
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
