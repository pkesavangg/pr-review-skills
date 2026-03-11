package com.dmdbrands.gurus.weight.features.ScaleUsers.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.features.ScaleUsers.components.ScaleUserList
import com.dmdbrands.gurus.weight.features.ScaleUsers.reducer.ScaleUserListIntent
import com.dmdbrands.gurus.weight.features.ScaleUsers.reducer.ScaleUserListState
import com.dmdbrands.gurus.weight.features.ScaleUsers.strings.ScaleUsersStrings
import com.dmdbrands.gurus.weight.features.ScaleUsers.viewmodel.ScaleUserListViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import kotlinx.collections.immutable.persistentListOf

@Composable
fun ScaleUserListScreen(scaleId: String) {
  val viewModel: ScaleUserListViewModel =
    hiltViewModel<ScaleUserListViewModel, ScaleUserListViewModel.Factory>(
      creationCallback = { factory -> factory.create(scaleId) },
    )
  val state by viewModel.state.collectAsState()

  BackHandler {
    viewModel.handleIntent(ScaleUserListIntent.Back)
  }

  ScaleUserListScreenContent(state, viewModel::handleIntent)
}

@Composable
fun ScaleUserListScreenContent(
  state: ScaleUserListState,
  handleIntent: (ScaleUserListIntent) -> Unit,
) {
  // Store the original username to compare against
  val originalUsername by remember {
    derivedStateOf {
      state.scale?.preferences?.displayName ?: ""
    }
  }

  val isSaveEnabled by remember(state.usernameForm.username.value, state.hasSetUsername, originalUsername) {
    derivedStateOf {
      val hasSetUsername = state.hasSetUsername
      val currentValue = state.usernameForm.username.value
      val valueNotEmpty = currentValue.isNotEmpty()
      val isValid = state.usernameForm.username.isValueValid()
      val hasChanged = currentValue != originalUsername
      hasSetUsername &&
        valueNotEmpty &&
        isValid &&
        hasChanged
    }
  }


  AppScaffold(
    title = ScaleUsersStrings.Header,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) {
        handleIntent(ScaleUserListIntent.Back)
      }
    },
    actions = {

      AppText(
        text = ScaleUsersStrings.SaveButton,
        textType = TextType.ListTitle1,
        color = if (isSaveEnabled) colorScheme.primaryAction else colorScheme.primaryActionDisabled,
        enabled = isSaveEnabled,
        modifier =
          Modifier
            .padding(end = spacing.md)
            .clickable(enabled = isSaveEnabled)
            { handleIntent(ScaleUserListIntent.Save) },
      )
    },
  ) {
    ScaleUserList(
      modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.md),
      userList = state.scaleUserList,
      userFormControl = state.usernameForm.username,
      onDeleteUser = { user -> handleIntent(ScaleUserListIntent.DeleteUser(user)) },
    )
  }
}

@PreviewTheme
@Composable
fun ScaleUserListScreenPreview() {
  val dummyState = ScaleUserListState(
    scaleUserList = persistentListOf(
      GGBTUser(
        name = "Poongs",
        token = "424443432323424324",
        lastActive = 1656720000, // July 02, 2022
        isBodyMetricsEnabled = true,
      ),
      GGBTUser(
        name = "Kaviya",
        token = "424443432323424325",
        lastActive = 1571702400, // October 17, 2019
        isBodyMetricsEnabled = true,
      ),
    ),
  )

  MeAppTheme {
    ScaleUserListScreenContent(
      state = dummyState,
      handleIntent = {},
    )
  }
}
