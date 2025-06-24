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
    @State private var showAppearancePicker: Bool = false
    @State private var showNotificationPicker: Bool = false
    @State private var showGenderPicker: Bool = false
    @State private var showUnitPicker: Bool = false
    @State private var showActivityPicker: Bool = false

    
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
        // Appearance picker
        .pickerSheet(
            isPresented: $showAppearancePicker,
            selectedValues: [Theme.shared.appearanceMode],
            options: [AppearanceMode.allCases],
            displayValue: { $0.rawValue },
            title: settingsLang.appearance,
            onUpdate: { vals in
                if let mode = vals.first {
                    Theme.shared.appearanceMode = mode
                }
            }
        )
        // Notifications picker
        .pickerSheet(
            isPresented: $showNotificationPicker,
            selectedValues: [settingsStore.notificationPreference],
            options: [NotificationPreference.allCases],
            displayValue: { $0.title },
            title: settingsLang.notifications,
            onUpdate: { vals in
                if let pref = vals.first {
                    settingsStore.updateNotificationPreference(pref)
                }
            }
        )

        // Height picker sheets
        .pickerSheet(
            isPresented: $settingsStore.showHeightInchesPicker,
            selectedValues: settingsStore.selectedHeightInches,
            options: settingsStore.heightInchesOptions,
            displayValue: { $0 },
            pickerType: .heightInches,
            title: settingsLang.height,
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
            title: settingsLang.height,
            onUpdate: { newValues in
                settingsStore.updateHeight(fromMetric: true, values: newValues)
            }
        )
        .pickerSheet(
            isPresented: $showGenderPicker,
            selectedValues: [settingsStore.activeAccount?.gender ?? .male],
            options: [Sex.allCases],
            displayValue: { $0.rawValue.capitalized },
            title: settingsLang.biologicalSex,
            onUpdate: { vals in
                if let sex = vals.first {
                    settingsStore.updateGender(sex)
                }
            }
        )
        // Unit picker
        .pickerSheet(
            isPresented: $showUnitPicker,
            selectedValues: [settingsStore.activeAccount?.weightSettings?.weightUnit ?? .lb],
            options: [[WeightUnit.lb, .kg]],
            displayValue: { unit in
                unit == .kg ? CommonStrings.unitKgCm : CommonStrings.pickerLbs
            },
            title: settingsLang.unitType,
            onUpdate: { vals in
                if let unit = vals.first {
                    settingsStore.updateWeightUnit(unit)
                }
            }
        )
        // Activity level picker
        .pickerSheet(
            isPresented: $showActivityPicker,
            selectedValues: [settingsStore.activeAccount?.weightSettings?.activityLevel ?? .normal],
            options: [[ActivityLevel.normal, ActivityLevel.athlete]],
            displayValue: { $0.rawValue.capitalized },
            title: settingsLang.activityLevel,
            onUpdate: { vals in
                if let level = vals.first {
                    settingsStore.updateActivityLevel(level)
                }
            }
        )
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
                    tabViewModel.showTabBar = false
                    router.navigate(to: .goal)
                }))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(
                title: settingsLang.biologicalSex,
                value: settingsStore.biologicalSexText,
                chevronType: .upDown,
                onTap: { showGenderPicker = true }))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(
                title: settingsLang.activityLevel,
                value: settingsStore.activityLevelText,
                chevronType: .upDown,
                onTap: { showActivityPicker = true }))
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
                    showUnitPicker = true
                }))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(
                title: settingsLang.weightless,
                value: settingsStore.weightlessText,
                onTap: {
                    tabViewModel.showTabBar = false
                    router.navigate(to: .weightless)
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
                onTap: { showNotificationPicker = true }))
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
            SettingsListItem(config: SettingsItemConfig(
                title: settingsLang.appearance,
                value: settingsStore.appearanceModeText,
                chevronType: .upDown,
                onTap: { showAppearancePicker = true }))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.appPermissions))
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
                    router.navigate(to: .help)
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
