package com.greatergoods.meapp.features.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.settings.viewmodel.SettingsViewModel

//
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
//     AppScaffold(title = SettingsScreenStrings.Title) {
//     }
//     Column(
//         modifier =
//             Modifier
//                 .fillMaxSize()
//                 .background(MeTheme.colorScheme.primaryBackground),
//     ) {
//         // Top Bar
//
//         Column(
//             modifier =
//                 Modifier
//                     .fillMaxSize()
//                     .verticalScroll(rememberScrollState())
//                     .padding(horizontal = MeTheme.spacing.medium),
//         ) {
//             Spacer(modifier = Modifier.height(MeTheme.spacing.large))
//
//             Spacer(modifier = Modifier.height(MeTheme.spacing.xLarge))
//
//             // Account Settings Section
//             SettingsSection(
//                 title = SettingsScreenStrings.AccountSettings,
//                 items =
//                     listOf(
//                         SettingsItem(
//                             title = SettingsScreenStrings.AddEditScales,
//                             showInfoIcon = true,
//                             onClick = { viewModel.onAddEditScalesClick() },
//                         ),
//                         SettingsItem(
//                             title = SettingsScreenStrings.Integrations,
//                             showInfoIcon = true,
//                             onClick = { viewModel.onIntegrationsClick() },
//                         ),
//                         SettingsItem(
//                             title = SettingsScreenStrings.ExportData,
//                             onClick = { viewModel.onExportDataClick() },
//                         ),
//                         SettingsItem(
//                             title = SettingsScreenStrings.ChangePassword,
//                             showInfoIcon = true,
//                             onClick = { viewModel.onChangePasswordClick() },
//                         ),
//                     ),
//             )
//
//             // Profile Settings Section
//             SettingsSection(
//                 title = SettingsScreenStrings.ProfileSettings,
//                 items =
//                     listOf(
//                         SettingsItem(
//                             title = SettingsScreenStrings.GoalSetting,
//                             showInfoIcon = true,
//                             onClick = { viewModel.onGoalSettingClick() },
//                         ),
//                         SettingsItem(
//                             title = SettingsScreenStrings.BiologicalSex,
//                             value = "Female",
//                             showInfoIcon = true,
//                             onClick = { viewModel.onBiologicalSexClick() },
//                         ),
//                         SettingsItem(
//                             title = SettingsScreenStrings.ActivityLevel,
//                             value = "Athlete",
//                             showInfoIcon = true,
//                             onClick = { viewModel.onActivityLevelClick() },
//                         ),
//                         SettingsItem(
//                             title = SettingsScreenStrings.Height,
//                             value = "5' 7\"",
//                             onClick = { viewModel.onHeightClick() },
//                         ),
//                         SettingsItem(
//                             title = SettingsScreenStrings.UnitType,
//                             value = "lbs & feet",
//                             showInfoIcon = true,
//                             onClick = { viewModel.onUnitTypeClick() },
//                         ),
//                         SettingsItem(
//                             title = SettingsScreenStrings.Weightless,
//                             value = "On",
//                             onClick = { viewModel.onWeightlessClick() },
//                         ),
//                     ),
//             )
//
//             // App Settings Section
//             SettingsSection(
//                 title = SettingsScreenStrings.AppSettings,
//                 items =
//                     listOf(
//                         SettingsItem(
//                             title = SettingsScreenStrings.Notifications,
//                             value = "Off",
//                             showInfoIcon = true,
//                             onClick = { viewModel.onNotificationsClick() },
//                         ),
//                         SettingsItem(
//                             title = SettingsScreenStrings.Messages,
//                             showInfoIcon = true,
//                             onClick = { viewModel.onMessagesClick() },
//                         ),
//                         SettingsItem(
//                             title = SettingsScreenStrings.AppPermissions,
//                             showInfoIcon = true,
//                             onClick = { viewModel.onAppPermissionsClick() },
//                         ),
//                     ),
//             )
//
//             // Support Section
//             SettingsSection(
//                 title = SettingsScreenStrings.Support,
//                 items =
//                     listOf(
//                         SettingsItem(
//                             title = SettingsScreenStrings.HelpCustomerService,
//                             showInfoIcon = true,
//                             onClick = { viewModel.onHelpClick() },
//                         ),
//                         SettingsItem(
//                             title = SettingsScreenStrings.PrivacyPolicy,
//                             showInfoIcon = true,
//                             onClick = { viewModel.onPrivacyPolicyClick() },
//                         ),
//                         SettingsItem(
//                             title = SettingsScreenStrings.TermsOfService,
//                             showInfoIcon = true,
//                             onClick = { viewModel.onTermsOfServiceClick() },
//                         ),
//                         SettingsItem(
//                             title = SettingsScreenStrings.GreaterGoodsDotCom,
//                             showInfoIcon = true,
//                             onClick = { viewModel.onGreaterGoodsClick() },
//                         ),
//                     ),
//             )
//
//             // Log Out and Delete Account
//             Card(
//                 modifier = Modifier.fillMaxWidth(),
//                 shape = RoundedCornerShape(MeTheme.borderRadius.medium),
//                 colors =
//                     CardDefaults.cardColors(
//                         containerColor = MeTheme.colorScheme.secondaryBackground,
//                     ),
//             ) {
//                 Column {
//                     SettingsItem(
//                         title = SettingsScreenStrings.LogOut,
//                         onClick = { viewModel.onLogOutClick() },
//                     )
//                     Divider(
//                         color = MeTheme.colorScheme.utility,
//                         thickness = 0.5.dp,
//                     )
//                     SettingsItem(
//                         title = SettingsScreenStrings.DeleteAccount,
//                         textColor = MeTheme.colorScheme.danger,
//                         onClick = { viewModel.onDeleteAccountClick() },
//                     )
//                 }
//             }
//
//             Spacer(modifier = Modifier.height(MeTheme.spacing.medium))
//         }
//     }
}
//
// @Composable
// private fun SettingsSection(
//     title: String,
//     items: List<SettingsItem>,
// ) {
//     Column(modifier = Modifier.fillMaxWidth()) {
//         Text(
//             text = title,
//             style = MeTheme.typography.headingLarge,
//             color = MeTheme.colorScheme.textHeading,
//             modifier = Modifier.padding(bottom = MeTheme.spacing.medium),
//         )
//
//         Card(
//             modifier = Modifier.fillMaxWidth(),
//             shape = RoundedCornerShape(MeTheme.borderRadius.medium),
//             colors =
//                 CardDefaults.cardColors(
//                     containerColor = MeTheme.colorScheme.secondaryBackground,
//                 ),
//         ) {
//             Column {
//                 items.forEachIndexed { index, item ->
//                     SettingsItemRow(item)
//                     if (index < items.size - 1) {
//                         Divider(
//                             color = MeTheme.colorScheme.utility,
//                             thickness = 0.5.dp,
//                         )
//                     }
//                 }
//             }
//         }
//
//         Spacer(modifier = Modifier.height(MeTheme.spacing.large))
//     }
// }
//
// @Composable
// private fun SettingsItemRow(item: SettingsItem) {
//     Surface(
//         modifier = Modifier.fillMaxWidth(),
//         color = MeTheme.colorScheme.secondaryBackground,
//         onClick = item.onClick,
//     ) {
//         Row(
//             modifier =
//                 Modifier
//                     .fillMaxWidth()
//                     .padding(MeTheme.spacing.medium),
//             horizontalArrangement = Arrangement.SpaceBetween,
//             verticalAlignment = Alignment.CenterVertically,
//         ) {
//             Text(
//                 text = item.title,
//                 style = MeTheme.typography.bodyLarge,
//                 color = item.textColor,
//             )
//
//             Row(
//                 horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.small),
//                 verticalAlignment = Alignment.CenterVertically,
//             ) {
//                 if (item.value != null) {
//                     Text(
//                         text = item.value,
//                         style = MeTheme.typography.bodyLarge,
//                         color = MeTheme.colorScheme.textSubheading,
//                         textAlign = TextAlign.End,
//                         modifier = Modifier.widthIn(max = 120.dp),
//                     )
//                 }
//                 if (item.showInfoIcon) {
//                     Icon(
//                         painter = rememberVectorPainter(Icons.Outlined.Info),
//                         contentDescription = null,
//                         tint = MeTheme.colorScheme.iconPrimary,
//                         modifier = Modifier.size(24.dp),
//                     )
//                 }
//             }
//         }
//     }
// }
//
// private data class SettingsItem(
//     val title: String,
//     val value: String? = null,
//     val showInfoIcon: Boolean = false,
//     val textColor: androidx.compose.ui.graphics.Color = MeTheme.colorScheme.textBody,
//     val onClick: () -> Unit = {},
// )
//
// @PreviewTheme
// @Composable
// fun SettingsScreenPreview() {
//     SettingsScreen(onNavigateBack = {})
// }
