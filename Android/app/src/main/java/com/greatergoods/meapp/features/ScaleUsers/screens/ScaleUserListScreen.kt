package com.greatergoods.meapp.features.ScaleUsers.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.features.ScaleUsers.components.ScaleUserList
import com.greatergoods.meapp.features.ScaleUsers.reducer.ScaleUserListIntent
import com.greatergoods.meapp.features.ScaleUsers.reducer.ScaleUserListState
import com.greatergoods.meapp.features.ScaleUsers.strings.ScaleUsersStrings
import com.greatergoods.meapp.features.ScaleUsers.viewmodel.ScaleUserListViewModel
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing
import kotlinx.coroutines.launch

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
  val backStack = LocalNavBackStack.current
  val coroutineScope = rememberCoroutineScope()

  AppScaffold(
    title = ScaleUsersStrings.Header,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) {
        coroutineScope.launch {
          backStack.removeLast()
        }
      }
    },
    actions = {
      AppText(
        text = ScaleUsersStrings.SaveButton,
        textType = TextType.ListTitle1,
        color = colorScheme.primaryAction,
        enabled = state.usernameForm.username.dirty || state.usernameForm.username.touched && state.hasSetUsername,
        modifier =
          Modifier
            .padding(end = spacing.md)
            .clickable { handleIntent(ScaleUserListIntent.Save) },
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
    scaleUserList = listOf(
      GGBTUser (
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
