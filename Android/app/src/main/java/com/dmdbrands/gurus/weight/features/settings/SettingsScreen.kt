package com.dmdbrands.gurus.weight.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.HeightInput
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.SettingsSection
import com.dmdbrands.gurus.weight.features.common.model.SettingColorType
import com.dmdbrands.gurus.weight.features.common.model.SettingsItem
import com.dmdbrands.gurus.weight.features.common.model.SettingsItemType
import com.dmdbrands.gurus.weight.features.settings.components.UserProfileSection
import com.dmdbrands.gurus.weight.features.settings.strings.SettingsScreenStrings
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsIntent
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsState
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsViewModel
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.launch

// TODO: A new folder 'screens' will be created under 'settings' for MyAccountsScreen, MaxAccountsReachedDialog, and RemoveAccountDialog.
// TODO: MyAccountsScreen and related dialogs/popups will be implemented in a new 'screens' folder under 'settings'.
// This follows the feature-based structure and keeps My Accounts logic modular and maintainable.
// TODO: Navigation to MyAccountsScreen will be added to the settings list.
// MyAccountsScreen will be implemented in a new file under 'screens'.

@Composable
fun SettingsScreen() {
  val viewmodel: SettingsViewModel = hiltViewModel()
  val state by viewmodel.state.collectAsState()
  SettingsScreenContent(state, viewmodel::handleIntent, viewmodel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
  state: SettingsState,
  handleIntent: (SettingsIntent) -> Unit,
  viewModel: SettingsViewModel? = null,
) {
  val coroutineScope = rememberCoroutineScope()
  val backStack = LocalNavBackStack.current
  AppScaffold(title = SettingsScreenStrings.Title) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(vertical = MeTheme.spacing.md, horizontal = MeTheme.spacing.sm)
    ) {
      UserProfileSection(
        account = state.account,
        onEditProfileClick = { },
        onAvatarLongPress = {
          handleIntent(SettingsIntent.SwitchAccount)
        },
      )
      Spacer(modifier = Modifier.height(MeTheme.spacing.xl))
      // Account Settings Section
      SettingsSection(
        title = SettingsScreenStrings.AccountSettings,
        items =
          listOf(
            SettingsItem(
              title = SettingsScreenStrings.AddEditScales,
              onClick = {
                handleIntent.invoke(SettingsIntent.OpenAddScales)
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.Integrations,
              onClick = {
                coroutineScope.launch {
                  backStack.addRoute(AppRoute.Integration.IntegrationList)
                }
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.ExportData,
              type = SettingsItemType.None,
              enabled = state.isExportEnabled,
              onClick = {
                handleIntent.invoke(SettingsIntent.ExportData)
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.ChangePassword,
              onClick = {
                coroutineScope.launch {
                  backStack.addRoute(AppRoute.AccountSettings.ChangePassword)
                }
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.UserProfile,
              onClick = {
                coroutineScope.launch {
                  backStack.addRoute(AppRoute.AccountSettings.Profile)
                }
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.DefaultGraphRange,
              type = SettingsItemType.Dropdown(state.currentDefaultGraphRange),
              onClick = {
                handleIntent.invoke(SettingsIntent.ShowDefaultGraphRangeModal)
              },
            ),
          ),
      )
      // Profile Settings Section
      SettingsSection(
        title = SettingsScreenStrings.ProfileSettings,
        items =
          listOf(
            SettingsItem(
              title = SettingsScreenStrings.GoalSetting,
              type = SettingsItemType.Action(),
              onClick = {
                handleIntent.invoke(SettingsIntent.goalSettingModal)
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.BiologicalSex,
              type =
                SettingsItemType.Dropdown(
                  state.account?.gender?.replaceFirstChar { it.uppercase() }
                    ?: SettingsScreenStrings.NotSet,
                ),
              onClick = {
                handleIntent.invoke(SettingsIntent.ShowBiologicalSexModal)
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.ActivityLevel,
              type = SettingsItemType.Dropdown(
                state.account?.activityLevel?.replaceFirstChar { it.uppercase() }
                  ?: SettingsScreenStrings.NotSet,
              ),
              onClick = {
                handleIntent.invoke(SettingsIntent.ShowActivityLevelModal)
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.Height,
              type = SettingsItemType.TextOnly(
                HeightInput.formatHeightDisplay(
                  height = state.account?.height,
                  isMetric = state.account?.weightUnit == WeightUnit.KG,
                ),
              ),
              onClick = {
                handleIntent.invoke(SettingsIntent.ShowHeightModal)
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.UnitType,
              type = SettingsItemType.Dropdown(
                state.account?.weightUnit?.unit ?: SettingsScreenStrings.NotSet,
              ),
              onClick = {
                handleIntent.invoke(SettingsIntent.ShowUnitTypeModal)
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.Weightless,
              type = SettingsItemType.Action(
                viewModel?.getWeightlessDisplayText() ?: "Off",
              ),
              onClick = {
                handleIntent.invoke(SettingsIntent.ShowWeightlessModal)
              },
            ),
          ),
      )
      // App Settings Section
      SettingsSection(
        title = SettingsScreenStrings.AppSettings,
        items =
          listOf(
            SettingsItem(
              title = SettingsScreenStrings.Notifications,
              type = SettingsItemType.Dropdown(state.currentNotificationStatus),
              onClick = {
                handleIntent.invoke(SettingsIntent.ShowNotificationsModal)
              },
            ),
            SettingsItem(
              title = if (state.unreadFeedCount > 0) {
               SettingsScreenStrings.MessagesWithCount(state.unreadFeedCount)
              } else {
                SettingsScreenStrings.Messages
              },
              type = SettingsItemType.Action(),
              showUnreadIndicator = state.showUnreadFeedIndication,
              onClick = {
                coroutineScope.launch {
                  backStack.addRoute(AppRoute.Feed.FeedMessages)
                }
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.AppPermissions,
              type = SettingsItemType.Action(),
              onClick = {
                coroutineScope.launch {
                  backStack.addRoute(AppRoute.AccountSettings.AppPermissions)
                }
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.Appearance,
              type = SettingsItemType.Dropdown(state.currentThemeMode),
              onClick = {
                handleIntent.invoke(SettingsIntent.ShowAppearanceModal)
              },
            ),
          ),
      )

      // Support Section
      SettingsSection(
        title = SettingsScreenStrings.Support,
        items =
          listOf(
            SettingsItem(
              title = SettingsScreenStrings.HelpCustomerService,
              type = SettingsItemType.Action(),
              onClick = {
                handleIntent(SettingsIntent.OpenHelp)
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.PrivacyPolicy,
              type = SettingsItemType.Action(),
              onClick = {
                handleIntent(SettingsIntent.OpenPrivacyPolicy)
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.TermsOfService,
              type = SettingsItemType.Action(),
              onClick = {
                handleIntent(SettingsIntent.OpenTermsOfService)
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.GreaterGoodsDotCom,
              type = SettingsItemType.Action(),
              onClick = {
                handleIntent(SettingsIntent.OpenGreaterGoodsWebsite)
              },
            ),
          ),
      )
      // Developer/Testing Section (only show when testing features are enabled)
      if (state.enableTestingFeatures) {
        SettingsSection(
          title = "Developer Options",
          items =
            listOf(
              SettingsItem(
                title = "0412 Scale Filter",
                type = SettingsItemType.Dropdown(state.selectedMacAddress),
                onClick = {
                  handleIntent(SettingsIntent.ShowMacAddressFilterModal)
                },
              ),
            ),
        )
      }

      // Log Out and Delete Account
      SettingsSection(
        items = buildList {
          add(
            SettingsItem(
              title = SettingsScreenStrings.SwitchAccounts,
              type = SettingsItemType.Action(),
              onClick = {
                handleIntent(SettingsIntent.SwitchAccount)
              },
            ),
          )
          add(
            SettingsItem(
              title = SettingsScreenStrings.LogOut,
              type = SettingsItemType.None,
              onClick = {
                handleIntent(SettingsIntent.Logout)
              },
            ),
          )
          if (state.hasMultipleAccounts) {
            add(
              SettingsItem(
                title = SettingsScreenStrings.LogoutAll,
                type = SettingsItemType.None,
                onClick = {
                  handleIntent(SettingsIntent.LogoutAllAccounts)
                },
              ),
            )
          }
          add(
            SettingsItem(
              title = SettingsScreenStrings.DeleteAccount,
              type = SettingsItemType.None,
              color = SettingColorType.Danger,
              onClick = { handleIntent(SettingsIntent.ConfirmDeleteAccount) },
            ),
          )
        },
      )
    }
  }
}

@PreviewTheme
@Composable
fun SettingsScreenPreview() {
  MeAppTheme {
    SettingsScreenContent(SettingsState(), { }, null)
  }
}
