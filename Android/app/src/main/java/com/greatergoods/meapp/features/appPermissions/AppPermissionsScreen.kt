package com.greatergoods.meapp.features.appPermissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.features.appPermissions.helper.AppPermissionsHelper
import com.greatergoods.meapp.features.appPermissions.strings.AppPermissionsScreenStrings
import com.greatergoods.meapp.features.appPermissions.viewmodel.AppPermissionsIntent
import com.greatergoods.meapp.features.appPermissions.viewmodel.AppPermissionsState
import com.greatergoods.meapp.features.appPermissions.viewmodel.AppPermissionsViewModel
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PermissionItem
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
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
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(vertical = MeTheme.spacing.md, horizontal = MeTheme.spacing.sm),
    ) {
      permissionGroups.forEach { group ->
        // Group header (section title)
        AppText(
          text = group.header,
          textType = TextType.ListTitle1,
          modifier = Modifier.padding(bottom = MeTheme.spacing.sm),
        )
        Column(
          modifier = Modifier
            .clip(shape = RoundedCornerShape(MeTheme.borderRadius.md))
            .background(color = MeTheme.colorScheme.primaryBackground),
        ) {
          group.items.forEachIndexed { index, item ->
            PermissionItem(
              item = item,
              onClick = { handleIntent(AppPermissionsIntent.RequestPermission(item.key)) },
            )
            if (index < group.items.size - 1) {
              HorizontalDivider(color = MeTheme.colorScheme.utility)
            }
          }
        }
        Spacer(modifier = Modifier.padding(MeTheme.spacing.sm))
      }
    }
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
