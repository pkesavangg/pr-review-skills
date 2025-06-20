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
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.common.components.AppScaffold
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

@Composable
fun SettingsScreen() {
    val viewmodel: SettingsViewModel = hiltViewModel()
    val state by viewmodel.state.collectAsState()
    SettingsScreenContent(state, viewmodel::handleIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    state: SettingsState,
    handleIntent: (SettingsIntent) -> Unit,
) {
    AppScaffold(title = SettingsScreenStrings.Title) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = MeTheme.spacing.md, horizontal = MeTheme.spacing.sm),
        ) {
            UserProfileSection(state.account) {}
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
                            onClick = { },
                        ),
                        SettingsItem(
                            title = SettingsScreenStrings.ChangePassword,
                            onClick = { },
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
                            type = SettingsItemType.Dropdown("Female"),
                            onClick = { },
                        ),
                        SettingsItem(
                            title = SettingsScreenStrings.ActivityLevel,
                            type = SettingsItemType.Dropdown("Athlete"),
                            onClick = { },
                        ),
                        SettingsItem(
                            title = SettingsScreenStrings.Height,
                            type = SettingsItemType.TextOnly("5' 7\""),
                            onClick = { },
                        ),
                        SettingsItem(
                            title = SettingsScreenStrings.UnitType,
                            type = SettingsItemType.Dropdown("lbs & feet"),
                            onClick = { },
                        ),
                        SettingsItem(
                            title = SettingsScreenStrings.Weightless,
                            type = SettingsItemType.TextOnly("On"),
                            onClick = { },
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
                            type = SettingsItemType.Dropdown("Off"),
                            onClick = { },
                        ),
                        SettingsItem(
                            title = SettingsScreenStrings.Messages,
                            type = SettingsItemType.Action(),
                            onClick = { },
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
                            onClick = { },
                        ),
                        SettingsItem(
                            title = SettingsScreenStrings.PrivacyPolicy,
                            type = SettingsItemType.Action(),
                            onClick = { },
                        ),
                        SettingsItem(
                            title = SettingsScreenStrings.TermsOfService,
                            type = SettingsItemType.Action(),
                            onClick = { },
                        ),
                        SettingsItem(
                            title = SettingsScreenStrings.GreaterGoodsDotCom,
                            type = SettingsItemType.Action(),
                            onClick = { },
                        ),
                    ),
            )

            // Log Out and Delete Account
            SettingsSection(
                items =
                    listOf(
                        SettingsItem(
                            title = SettingsScreenStrings.SwitchAccounts,
                            type = SettingsItemType.None,
                            onClick = { },
                        ),
                        SettingsItem(
                            title = SettingsScreenStrings.LogOut,
                            type = SettingsItemType.None,
                            onClick = {
                                handleIntent(SettingsIntent.Logout)
                            },
                        ),
                        SettingsItem(
                            title = SettingsScreenStrings.DeleteAccount,
                            type = SettingsItemType.None,
                            color = SettingColorType.Danger,
                            onClick = { },
                        ),
                    ),
            )
        }
    }
}

@PreviewTheme
@Composable
fun SettingsScreenPreview() {
    MeAppTheme {
        SettingsScreenContent(SettingsState(), {  })
    }
}
