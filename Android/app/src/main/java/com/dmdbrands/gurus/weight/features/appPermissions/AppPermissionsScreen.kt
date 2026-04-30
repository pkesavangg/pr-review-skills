package com.dmdbrands.gurus.weight.features.appPermissions

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.appPermissions.strings.AppPermissionsScreenStrings
import com.dmdbrands.gurus.weight.features.appPermissions.viewmodel.AppPermissionsIntent
import com.dmdbrands.gurus.weight.features.appPermissions.viewmodel.AppPermissionsState
import com.dmdbrands.gurus.weight.features.appPermissions.viewmodel.AppPermissionsViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.permissionSettings.PermissionSettings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.launch

/**
 * App Permissions screen that displays the status of various Android permissions
 * and allows users to manage them.
 */
@Composable
fun AppPermissionsScreen() {
  val viewModel: AppPermissionsViewModel = hiltViewModel()
  val state by viewModel.state.collectAsState()

  AppPermissionsContent(state, viewModel::handleIntent)
}

/**
 * Content composable for the App Permissions screen.
 */
@Composable
fun AppPermissionsContent(
  state: AppPermissionsState,
  handleIntent: (AppPermissionsIntent) -> Unit,
) {
  val navBackStack = LocalNavBackStack.current
  val scope = rememberCoroutineScope()
  val permissionGroups = AppPermissionsHelper.mapToPermissionGroups(state.permissionMap)

  AppScaffold(
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) {
        scope.launch {
          navBackStack.removeLast()
        }
      }
    },
    title = AppPermissionsScreenStrings.Title,
  ) {
    PermissionSettings(
      modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = MeTheme.spacing.md, horizontal = MeTheme.spacing.sm),
      permissionGroups = permissionGroups,
      onRequestPermission = { permissionType ->
        handleIntent(AppPermissionsIntent.RequestPermission(permissionType))
      },
      requiredPermissions = state.requiredPermissions // Show red for required unauthorized permissions
    )
  }
}

@PreviewTheme
@Composable
fun AppPermissionsScreenPreview() {
  MeAppTheme {
    AppPermissionsContent(
      state = AppPermissionsState(),
      handleIntent = {},
    )
  }
}
