//
//  SettingsScreen.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//

import SwiftUI

// MARK: - SettingsScreen
/// Represents the settings screen of the application.
/// This screen allows users to configure various application settings.
struct SettingsScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var tabViewModel: BottomTabBarViewModel
    @StateObject var settingsStore = SettingsStore()
    @StateObject private var router = Router<SettingsRoute>()
    // Dialog state controls
    @State private var showingAppearanceDialog: Bool = false
    @State private var showingUnitDialog: Bool = false
    @State private var showingGenderDialog: Bool = false
    @State private var showingActivityDialog: Bool = false
    @State private var showingNotificationDialog: Bool = false

    
    let settingsLang = SettingsStrings.self
    let commonLang = CommonStrings.self
    let labels = InputFieldLabels.self
    let appAssets = AppAssets.self
    var body: some View {
        RoutingView(stack: $router.stack) {
            VStack(spacing: 0) {
                NavbarHeaderView<EmptyView, EmptyView>(title: settingsLang.title, canShowBorder: true)
                ZStack {
                    theme.backgroundSecondary
                        .ignoresSafeArea()
                    VStack(spacing: 0) {
                        List {
                            profileHeader()
                            accountSettingsSection()
                            profileSettingsSection()
                            appSettingsSection()
                            supportSection()
                            accountActionSection()
                        }
                        .listStyle(.insetGrouped)
                        .scrollContentBackground(.hidden)
                    }
                }
                .inAppBrowser(
                    url: settingsStore.presentingBrowserURL,
                    isPresented: settingsStore.isBrowserPresented
                )
            }
            .onAppear {
                tabViewModel.showTabBar = true
            }
        }
        .environmentObject(router)
        .environmentObject(settingsStore)
        // Appearance selection dialog
        .confirmationDialog(
            settingsLang.appearance,
            isPresented: $showingAppearanceDialog,
            titleVisibility: .visible
        ) {
            ForEach(AppearanceMode.allCases, id: \.self) { mode in
                Button(mode.rawValue) {
                    Theme.shared.appearanceMode = mode
                }
            }
            Button(CommonStrings.cancel, role: .cancel) {}
        }
        // Unit selection dialog
        .confirmationDialog(
            settingsLang.unitType,
            isPresented: $showingUnitDialog,
            titleVisibility: .visible
        ) {
            Button(CommonStrings.unitLbsFeet) {
                settingsStore.updateWeightUnit(.lb)
            }
            Button(CommonStrings.unitKgCm) {
                settingsStore.updateWeightUnit(.kg)
            }
            Button(CommonStrings.cancel, role: .cancel) {}
        }
        .sheet(isPresented: $settingsStore.showWeightLessPage, content: {
            WeightlessScreen()
                .environmentObject(settingsStore)
                .interactiveDismissDisabled()
        })
        
        // Gender dialog
        .confirmationDialog(
            settingsLang.biologicalSex,
            isPresented: $showingGenderDialog,
            titleVisibility: .visible
        ) {
            Button(Sex.male.rawValue.capitalized) { settingsStore.updateGender(.male) }
            Button(Sex.female.rawValue.capitalized) { settingsStore.updateGender(.female) }
            Button(CommonStrings.cancel, role: .cancel) {}
        }
        // Activity level dialog
        .confirmationDialog(
            settingsLang.activityLevel,
            isPresented: $showingActivityDialog,
            titleVisibility: .visible
        ) {
            Button(ActivityLevel.normal.rawValue.capitalized) { settingsStore.updateActivityLevel(.normal) }
            Button(ActivityLevel.athlete.rawValue.capitalized) { settingsStore.updateActivityLevel(.athlete) }
            Button(CommonStrings.cancel, role: .cancel) {}
        }
        // Notifications dialog
        .confirmationDialog(
            settingsLang.notifications,
            isPresented: $showingNotificationDialog,
            titleVisibility: .visible
        ) {
            ForEach(NotificationPreference.allCases, id: \.self) { pref in
                Button(pref.title) { settingsStore.updateNotificationPreference(pref) }
            }
            Button(CommonStrings.cancel, role: .cancel) {}
        }

        // Height picker sheets
        .pickerSheet(
            isPresented: $settingsStore.showHeightInchesPicker,
            selectedValues: settingsStore.selectedHeightInches,
            options: settingsStore.heightInchesOptions,
            displayValue: { $0 },
            pickerType: .heightInches,
            onUpdate: { newValues in
                settingsStore.updateHeight(fromMetric: false, values: newValues)
            }
        )
        .pickerSheet(
            isPresented: $settingsStore.showHeightCmPicker,
            selectedValues: settingsStore.selectedHeightCm,
            options: settingsStore.heightCmOptions,
            displayValue: { $0 },
            pickerType: .heightCm,
            onUpdate: { newValues in
                settingsStore.updateHeight(fromMetric: true, values: newValues)
            }
        )
        .sheet(isPresented: $settingsStore.showGoalPage, content: {
            GoalSettingScreen()
                .environmentObject(settingsStore)
                .interactiveDismissDisabled()
        })
    }
    
    private func profileHeader() -> some View {
        VStack(spacing: .spacingXS) {
            InitialIconView(
                character: settingsStore.profileInitial,
                size: 36,
                style: .fill
            )
            Text(settingsStore.profileName)
                .fontOpenSans(.heading3)
                .foregroundColor(theme.textHeading)
            Text(settingsStore.profileEmail)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
        }
        .frame(maxWidth: .infinity)
        .listRowBackground(Color.clear)
    }
    
    private func accountSettingsSection() -> some View {
        Section(header: sectionHeader(title: settingsLang.accountSettings)) {
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.addEditScales))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.integrations))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.exportData, chevronType: .none, onTap: {
                settingsStore.handleExport()
            }))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.changePassword,
                                                       onTap: {
                                                           tabViewModel.showTabBar = false
                                                           router.navigate(to: .changePassword)
                                                       }))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.userProfile,
                                                       onTap: {
                                                           tabViewModel.showTabBar = false
                                                           router.navigate(to: .editProfile)
                                                       }))
                .settingsRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    
    private func profileSettingsSection() -> some View {
        Section(header: sectionHeader(title: settingsLang.profileSettings)) {
            SettingsListItem(config: SettingsItemConfig(
                title: settingsLang.goalSetting,
                onTap: {
                    settingsStore.showGoalPage = true
                }))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(
                title: settingsLang.biologicalSex,
                value: settingsStore.biologicalSexText,
                chevronType: .upDown,
                onTap: { showingGenderDialog = true }))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(
                title: settingsLang.activityLevel,
                value: settingsStore.activityLevelText,
                chevronType: .upDown,
                onTap: { showingActivityDialog = true }))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.height, value: settingsStore.heightText, chevronType: .upDown, onTap: {
                settingsStore.showHeightPicker()
            }))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(
                title: settingsLang.unitType,
                value: settingsStore.unitTypeText,
                chevronType: .upDown,
                onTap: {
                    showingUnitDialog = true
                }))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(
                title: settingsLang.weightless,
                value: settingsStore.weightlessText,
                onTap: {
                    settingsStore.showWeightLessPage = true
                }))
                .settingsRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    
    private func appSettingsSection() -> some View {
        Section(header: sectionHeader(title: settingsLang.appSettings)) {
            SettingsListItem(config: SettingsItemConfig(
                title: settingsLang.notifications,
                value: settingsStore.notificationsOnText,
                chevronType: .upDown,
                onTap: { showingNotificationDialog = true }))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.messages, showDot: settingsStore.hasUnreadMessages))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(
                title: settingsLang.streaks,
                chevronType: .none, 
                toggleBinding: $settingsStore.streaksEnabled,
                onTap: { 
                    settingsStore.updateStreakStatus(settingsStore.streaksEnabled)
                }))
                .settingsRowInsets(top: 0, bottom: 0)
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.appPermissions))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(
                title: settingsLang.appearance,
                value: settingsStore.appearanceModeText,
                chevronType: .upDown,
                onTap: {
                    showingAppearanceDialog = true
                }))
                .settingsRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    
    private func supportSection() -> some View {
        Section(header:
                sectionHeader(title: settingsLang.supportSettings)
        ) {
            SettingsListItem(config: SettingsItemConfig(
                title: settingsLang.helpAndCustomerService,
                onTap: {
                    settingsStore.openHelp()
                }
            ))
            .settingsRowInsets()
            
            SettingsListItem(config: SettingsItemConfig(
                title: settingsLang.privacyPolicy,
                onTap: {
                    settingsStore.openPrivacy()
                }
            ))
            .settingsRowInsets()
            
            SettingsListItem(config: SettingsItemConfig(
                title: settingsLang.termsOfService,
                onTap: {
                    settingsStore.openTerms()
                }
            ))
            .settingsRowInsets()
            
            SettingsListItem(config: SettingsItemConfig(
                title: settingsLang.greaterGoodsWebsite,
                onTap: {
                    settingsStore.openGreaterGoods()
                }
            ))
            .settingsRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    
    private func accountActionSection() -> some View {
        Section {
            SettingsListItem(config: SettingsItemConfig(
                title: settingsLang.logOut,
                chevronType: .none,
                onTap: {
                    settingsStore.handleLogout()
                }
            ))
            .settingsRowInsets()
            
            SettingsListItem(config: SettingsItemConfig(
                title: settingsLang.deleteAccount.uppercased(),
                chevronType: .none,
                isDestructive: true,
                onTap: {
                    settingsStore.handleDeleteAccount()
                }
            ))
            .settingsRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    
    private func sectionHeader(title: String) -> some View {
        Text(title)
            .fontOpenSans(.heading4)
            .foregroundColor(theme.textHeading)
            .textCase(.none)
            .padding(.bottom, .spacingXS)
            .padding(.leading, -16)
    }
}

#Preview {
    SettingsScreen()
}
