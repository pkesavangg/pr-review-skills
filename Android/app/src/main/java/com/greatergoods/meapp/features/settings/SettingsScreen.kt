package com.greatergoods.meapp.features.settings

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
import com.greatergoods.meapp.core.navigation.AppRoute
import com.greatergoods.meapp.core.navigation.LocalNavBackStack
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.HeightInput
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.SettingsSection
import com.greatergoods.meapp.features.common.model.SettingColorType
import com.greatergoods.meapp.features.common.model.SettingsItem
import com.greatergoods.meapp.features.common.model.SettingsItemType
import com.greatergoods.meapp.features.settings.components.UserProfileSection
import com.greatergoods.meapp.features.settings.strings.SettingsScreenStrings
import com.greatergoods.meapp.features.settings.viewmodel.SettingsIntent
import com.greatergoods.meapp.features.settings.viewmodel.SettingsState
import com.greatergoods.meapp.features.settings.viewmodel.SettingsViewModel
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
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
                    .padding(vertical = MeTheme.spacing.md, horizontal = MeTheme.spacing.sm),
        ) {
            UserProfileSection(state.account) {
            }
            Spacer(modifier = Modifier.height(MeTheme.spacing.xl))
            // Account Settings Section
            SettingsSection(
                title = SettingsScreenStrings.AccountSettings,
                items =
                    listOf(
                        SettingsItem(
                            title = SettingsScreenStrings.AddEditScales,
                            onClick = { },
                        ),
                        SettingsItem(
                            title = SettingsScreenStrings.Integrations,
                            onClick = { },
                        ),
                        SettingsItem(
                            title = SettingsScreenStrings.ExportData,
                            type = SettingsItemType.None,
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
                            onClick = { },
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
                                state.account?.activityLevel?.replaceFirstChar { it.uppercase() } ?: SettingsScreenStrings.NotSet
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
                                    isMetric = state.account?.weightUnit?.value == "kg"
                                )
                            ),
                            onClick = {
                                handleIntent.invoke(SettingsIntent.ShowHeightModal)
                            },
                        ),
                        SettingsItem(
                            title = SettingsScreenStrings.UnitType,
                            type = SettingsItemType.Dropdown(
                                when (state.account?.weightUnit?.value) {
                                    "kg" -> "kg & cm"
                                    "lb" -> "lbs & feet"
                                    else -> SettingsScreenStrings.NotSet
                                }
                            ),
                            onClick = {
                                handleIntent.invoke(SettingsIntent.ShowUnitTypeModal)
                            },
                        ),
                        SettingsItem(
                            title = SettingsScreenStrings.Weightless,
                            type = SettingsItemType.Dropdown(
                                viewModel?.getWeightlessDisplayText() ?: "Off"
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
                            title = SettingsScreenStrings.Messages,
                            type = SettingsItemType.Action(),
                            onClick = { },
                        ),
                        SettingsItem(
                            title = SettingsScreenStrings.Streaks,
                            type = SettingsItemType.Dropdown(
                                if (state.account?.isStreakOn == true) "On" else "Off"
                            ),
                            onClick = {
                                handleIntent.invoke(SettingsIntent.ShowStreakModal)
                            },
                        ),
                        SettingsItem(
                            title = SettingsScreenStrings.AppPermissions,
                            type = SettingsItemType.Action(),
                            onClick = { },
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
                            onClick = {},
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

            // Log Out and Delete Account
            SettingsSection(
                items = buildList {
                    add(
                        SettingsItem(
                            title = SettingsScreenStrings.SwitchAccounts,
                            type = SettingsItemType.None,
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
                            onClick = { },
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
