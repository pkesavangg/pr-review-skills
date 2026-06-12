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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmdbrands.gurus.weight.BuildConfig
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.navigation.LocalNavBackStack
import com.dmdbrands.gurus.weight.features.addScale.strings.AddScaleScreenStrings
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.SettingsSection
import com.dmdbrands.gurus.weight.features.common.model.SettingColorType
import com.dmdbrands.gurus.weight.features.common.model.SettingsItem
import com.dmdbrands.gurus.weight.features.common.model.SettingsItemType
import com.dmdbrands.gurus.weight.features.settings.components.UserProfileSection
import com.dmdbrands.gurus.weight.features.settings.strings.SettingsScreenStrings
import com.dmdbrands.gurus.weight.features.settings.strings.toDisplayString
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsIntent
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsState
import com.dmdbrands.gurus.weight.features.settings.viewmodel.SettingsViewModel
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
  val viewmodel: SettingsViewModel = hiltViewModel()
  val state by viewmodel.state.collectAsStateWithLifecycle()
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
        title = SettingsScreenStrings.Account,
        items = buildList {
          add(
            SettingsItem(
              title = SettingsScreenStrings.UserProfile,
              testTag = "settings_row_user_profile",
              onClick = {
                coroutineScope.launch {
                  backStack.addRoute(AppRoute.AccountSettings.Profile)
                }
              },
            ),
          )
          // "My Kids" always appears; enabled only when the account owns a baby
          // scale (device-driven), not gated on an existing baby profile. (MOB-416)
          add(
            SettingsItem(
              title = SettingsScreenStrings.MyKids,
              enabled = state.hasBabyScaleDevice,
              onClick = {
                coroutineScope.launch {
                  backStack.addRoute(AppRoute.AccountSettings.MyKids)
                }
              },
            ),
          )
          add(
            SettingsItem(
              title = SettingsScreenStrings.MyDevices,
              testTag = "settings_row_my_devices",
              onClick = {
                handleIntent.invoke(SettingsIntent.OpenMyDevices)
              },
            ),
          )
          add(
            SettingsItem(
              title = SettingsScreenStrings.Integrations,
              testTag = "settings_row_integrations",
              onClick = {
                coroutineScope.launch {
                  backStack.addRoute(AppRoute.Integration.IntegrationList)
                }
              },
            ),
          )
          add(
            SettingsItem(
              title = SettingsScreenStrings.ChangePassword,
              testTag = "settings_row_change_password",
              onClick = {
                coroutineScope.launch {
                  backStack.addRoute(AppRoute.AccountSettings.ChangePassword)
                }
              },
            ),
          )
        },
      )

      // App Settings Section
      SettingsSection(
        title = SettingsScreenStrings.App,
        items =
          listOf(
            SettingsItem(
              title = SettingsScreenStrings.UnitType,
              type = SettingsItemType.Dropdown(
                state.account?.weightUnit?.unit ?: SettingsScreenStrings.NotSet,
              ),
              testTag = "settings_row_unit_type",
              onClick = {
                handleIntent.invoke(SettingsIntent.ShowUnitTypeModal)
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.Permissions,
              type = SettingsItemType.Action(),
              testTag = "settings_row_permissions",
              onClick = {
                coroutineScope.launch {
                  backStack.addRoute(AppRoute.AccountSettings.AppPermissions)
                }
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.Notifications,
              type = SettingsItemType.Dropdown(state.currentNotificationStatus),
              testTag = "settings_row_notifications",
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
              testTag = "settings_row_messages",
              onClick = {
                coroutineScope.launch {
                  backStack.addRoute(AppRoute.Feed.FeedMessages)
                }
              },
            ),

            SettingsItem(
              title = SettingsScreenStrings.Appearance,
              type = SettingsItemType.Dropdown(state.currentThemeMode),
              testTag = "settings_row_appearance",
              onClick = {
                handleIntent.invoke(SettingsIntent.ShowAppearanceModal)
              },
            ),
          ),
      )

      // Weight Scale Section
      SettingsSection(
        title = SettingsScreenStrings.WeightScale,
        items =
          listOf(
            SettingsItem(
              title = SettingsScreenStrings.GoalSetting,
              type = SettingsItemType.Action(),
              testTag = "settings_row_goal_setting",
              onClick = {
                handleIntent.invoke(SettingsIntent.goalSettingModal)
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.ActivityLevel,
              type = SettingsItemType.Dropdown(
                state.account?.activityLevel?.replaceFirstChar { it.uppercase() }
                  ?: SettingsScreenStrings.NotSet,
              ),
              testTag = "settings_row_activity_level",
              onClick = {
                handleIntent.invoke(SettingsIntent.ShowActivityLevelModal)
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.Weightless,
              type = SettingsItemType.Action(
                viewModel?.getWeightlessDisplayText() ?: "Off",
              ),
              testTag = "settings_row_weightless",
              onClick = {
                handleIntent.invoke(SettingsIntent.NavigateToWeightless)
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
              title = SettingsScreenStrings.Help,
              type = SettingsItemType.Action(),
              testTag = "settings_row_help",
              onClick = {
                handleIntent(SettingsIntent.OpenHelp)
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.PrivacyPolicy,
              type = SettingsItemType.Action(),
              testTag = "settings_row_privacy_policy",
              onClick = {
                handleIntent(SettingsIntent.OpenPrivacyPolicy)
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.TermsOfService,
              type = SettingsItemType.Action(),
              testTag = "settings_row_terms_of_service",
              onClick = {
                handleIntent(SettingsIntent.OpenTermsOfService)
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.GreaterGoodsDotCom,
              type = SettingsItemType.Action(),
              testTag = "settings_row_greater_goods",
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
          items = buildList {
            add(
              SettingsItem(
                title = "0412 Scale Filter",
                type = SettingsItemType.Dropdown(state.selectedMacAddress),
                onClick = {
                  handleIntent(SettingsIntent.ShowMacAddressFilterModal)
                },
              ),
            )
            if (BuildConfig.DEBUG) {
              add(
                SettingsItem(
                  title = "A3 Monitor Setup (Preview)",
                  type = SettingsItemType.Action(),
                  onClick = {
                    handleIntent(SettingsIntent.OpenA3MonitorSetup)
                  },
                ),
              )
              add(
                SettingsItem(
                  title = "Test Crash (Fatal)",
                  type = SettingsItemType.None,
                  color = SettingColorType.Danger,
                  onClick = {
                    handleIntent(SettingsIntent.TriggerTestCrash)
                  },
                ),
              )
              add(
                SettingsItem(
                  title = "Test Crash (Non-Fatal)",
                  type = SettingsItemType.None,
                  onClick = {
                    handleIntent(SettingsIntent.TriggerTestNonFatal)
                  },
                ),
              )
            }
          },
        )
      }

      // Log Out and Delete Account
      SettingsSection(
        items = buildList {
          add(
            SettingsItem(
              title = SettingsScreenStrings.SwitchAccounts,
              type = SettingsItemType.Action(),
              testTag = "settings_row_switch_accounts",
              onClick = {
                handleIntent(SettingsIntent.SwitchAccount)
              },
            ),
          )
          add(
            SettingsItem(
              title = SettingsScreenStrings.LogOut,
              type = SettingsItemType.None,
              testTag = "settings_row_log_out",
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
                testTag = "settings_row_logout_all",
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
              testTag = "settings_row_delete_account",
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
