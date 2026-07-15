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
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import com.dmdbrands.gurus.weight.features.addDevice.strings.AddDeviceScreenStrings
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
              testTag = TestTags.Settings.UserProfile,
              onClick = {
                coroutineScope.launch {
                  backStack.addRoute(AppRoute.AccountSettings.Profile)
                }
              },
            ),
          )
          // "My Kids" always appears; enabled once the account has engaged a baby scale
          // (productTypes carries "baby") or currently owns one. Additive per MOB-686 Rule A:
          // stays enabled after the device is removed. Not gated on an existing baby profile.
          add(
            SettingsItem(
              title = SettingsScreenStrings.MyKids,
              enabled = state.isMyKidsEnabled,
              testTag = TestTags.Settings.MyKids,
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
              testTag = TestTags.Settings.MyDevices,
              onClick = {
                handleIntent.invoke(SettingsIntent.OpenMyDevices)
              },
            ),
          )
          // Hidden for baby-scale only accounts — integrations apply to weight/BP data only.
          if (state.showIntegrations) {
            add(
              SettingsItem(
                title = SettingsScreenStrings.Integrations,
                testTag = TestTags.Settings.Integrations,
                onClick = {
                  coroutineScope.launch {
                    backStack.addRoute(AppRoute.Integration.IntegrationList)
                  }
                },
              ),
            )
          }
          add(
            SettingsItem(
              title = SettingsScreenStrings.ChangePassword,
              testTag = TestTags.Settings.ChangePassword,
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
          buildList {
            // Always shown (MOB-1175): even BP-only accounts see it, with both
            // sections locked to the default unit + an unlock message inside the dialog.
            if (state.showUnitType) {
              add(
                SettingsItem(
                  title = SettingsScreenStrings.UnitType,
                  type = SettingsItemType.Dropdown(
                    state.account?.weightUnit?.unit ?: SettingsScreenStrings.NotSet,
                  ),
                  testTag = TestTags.Settings.UnitType,
                  onClick = {
                    handleIntent.invoke(SettingsIntent.ShowUnitTypeModal)
                  },
                ),
              )
            }
            add(
              SettingsItem(
                title = SettingsScreenStrings.Permissions,
                type = SettingsItemType.Action(),
                testTag = TestTags.Settings.Permissions,
                onClick = {
                  coroutineScope.launch {
                    backStack.addRoute(AppRoute.AccountSettings.AppPermissions)
                  }
                },
              ),
            )
            add(
              SettingsItem(
                title = if (state.unreadFeedCount > 0) {
                  SettingsScreenStrings.MessagesWithCount(state.unreadFeedCount)
                } else {
                  SettingsScreenStrings.Messages
                },
                type = SettingsItemType.Action(),
                showUnreadIndicator = state.showUnreadFeedIndication,
                testTag = TestTags.Settings.Messages,
                onClick = {
                  coroutineScope.launch {
                    backStack.addRoute(AppRoute.Feed.FeedMessages)
                  }
                },
              ),
            )
            add(
              SettingsItem(
                title = SettingsScreenStrings.Appearance,
                type = SettingsItemType.Dropdown(state.currentThemeMode),
                testTag = TestTags.Settings.Appearance,
                onClick = {
                  handleIntent.invoke(SettingsIntent.ShowAppearanceModal)
                },
              ),
            )
          },
      )

      // My Weight Section — shown when the account owns the weight product
      // ([Account.productTypes] carries "weight"), which is set at signup for a weight-scale
      // profile and stays on thereafter. Keyed off product ownership (isMyWeightEnabled), NOT
      // the hasWeightScale device flag, so a weight account sees this section before any scale
      // is paired; BP-only / baby-only / BP+baby accounts (no "weight" product) stay hidden. (MOB-1455)
      if (state.isMyWeightEnabled) {
        SettingsSection(
          title = SettingsScreenStrings.WeightScale,
          items =
            listOf(
              SettingsItem(
                title = SettingsScreenStrings.Notifications,
                type = SettingsItemType.Dropdown(state.currentNotificationStatus),
                testTag = TestTags.Settings.Notifications,
                onClick = {
                  handleIntent.invoke(SettingsIntent.ShowNotificationsModal)
                },
              ),
              SettingsItem(
                title = SettingsScreenStrings.GoalSetting,
                type = SettingsItemType.Action(),
                testTag = TestTags.Settings.GoalSetting,
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
                testTag = TestTags.Settings.ActivityLevel,
                onClick = {
                  handleIntent.invoke(SettingsIntent.ShowActivityLevelModal)
                },
              ),
              SettingsItem(
                title = SettingsScreenStrings.Weightless,
                type = SettingsItemType.Action(
                  viewModel?.getWeightlessDisplayText() ?: "Off",
                ),
                testTag = TestTags.Settings.Weightless,
                onClick = {
                  handleIntent.invoke(SettingsIntent.NavigateToWeightless)
                },
              ),
            ),
        )
      }

      // Support Section
      SettingsSection(
        title = SettingsScreenStrings.Support,
        items =
          listOf(
            SettingsItem(
              title = SettingsScreenStrings.Help,
              type = SettingsItemType.Action(),
              testTag = TestTags.Settings.Help,
              onClick = {
                handleIntent(SettingsIntent.OpenHelp)
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.PrivacyPolicy,
              type = SettingsItemType.Action(),
              testTag = TestTags.Settings.PrivacyPolicy,
              onClick = {
                handleIntent(SettingsIntent.OpenPrivacyPolicy)
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.TermsOfService,
              type = SettingsItemType.Action(),
              testTag = TestTags.Settings.TermsOfService,
              onClick = {
                handleIntent(SettingsIntent.OpenTermsOfService)
              },
            ),
            SettingsItem(
              title = SettingsScreenStrings.GreaterGoodsDotCom,
              type = SettingsItemType.Action(),
              testTag = TestTags.Settings.GreaterGoods,
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
              testTag = TestTags.Settings.SwitchAccounts,
              onClick = {
                handleIntent(SettingsIntent.SwitchAccount)
              },
            ),
          )
          add(
            SettingsItem(
              title = SettingsScreenStrings.LogOut,
              type = SettingsItemType.None,
              testTag = TestTags.Settings.LogOut,
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
                testTag = TestTags.Settings.LogoutAll,
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
              testTag = TestTags.Settings.DeleteAccount,
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
