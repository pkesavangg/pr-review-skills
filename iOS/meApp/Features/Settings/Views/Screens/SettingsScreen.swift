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
    // App-wide appearance picker state now in store
    
    
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
                // Ensure this is the actively selected tab before evaluating modal presentation
                if tabViewModel.selectedTab == .settings {
                    settingsStore.presentAddAccountModalIfNeeded(router: router)
                    
                    // Handle any pending navigation request coming from BottomTabBarViewModel (e.g. Apple Health Connect)
                    if let route = tabViewModel.pendingSettingsNavigation {
                        tabViewModel.pendingSettingsNavigation = nil
                        router.navigate(to: route)
                    }
                }
            }
            // Re-evaluate modal presentation whenever the selected tab changes.
            .onChange(of: tabViewModel.selectedTab) {
                if tabViewModel.selectedTab == .settings {
                    settingsStore.presentAddAccountModalIfNeeded(router: router)
                    
                    if let route = tabViewModel.pendingSettingsNavigation {
                        tabViewModel.pendingSettingsNavigation = nil
                        router.navigate(to: route)
                    }
                }
            }
        }
        .environmentObject(router)
        .environmentObject(settingsStore)
        // Appearance picker fallback for non-iPad or iOS>=18
        .pickerSheet(
            isPresented: $settingsStore.showAppearancePicker,
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
            isPresented: $settingsStore.showNotificationPicker,
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
            isPresented: $settingsStore.showGenderPicker,
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
            isPresented: $settingsStore.showUnitPicker,
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
            isPresented: $settingsStore.showActivityPicker,
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
            .onLongPressGesture {
                router.navigate(to: .myAccounts)
            }
            Text(settingsStore.profileName)
                .fontOpenSans(.heading3)
                .foregroundColor(theme.textHeading)
                .lineLimit(1)
                .truncationMode(.tail)
            Text(settingsStore.profileEmail)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
        }
        .frame(maxWidth: .infinity)
        .listRowBackground(Color.clear)
    }
    
    private func accountSettingsSection() -> some View {
        Section(header: sectionHeader(title: settingsLang.accountSettings)) {
            ActionListItemView(config: ActionListItemConfig(title: settingsLang.addEditScales,
                                                            onTap: {router.navigate(to:.addEditScales)}))
            .listRowInsets()
            ActionListItemView(config: ActionListItemConfig(title: settingsLang.integrations, onTap: {
                router.navigate(to: .integrations)
            }))
            .listRowInsets()
            ActionListItemView(config: ActionListItemConfig(title: settingsLang.exportData, chevronType: .none, onTap: {
                settingsStore.handleExport()
            }))
            .listRowInsets()
            ActionListItemView(config: ActionListItemConfig(title: settingsLang.changePassword,
                                                            onTap: {
                router.navigate(to: .changePassword)
            }))
            .listRowInsets()
            ActionListItemView(config: ActionListItemConfig(title: settingsLang.userProfile,
                                                            onTap: {
                router.navigate(to: .editProfile)
            }))
            .listRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }
    
    private func profileSettingsSection() -> some View {
        Section(header: sectionHeader(title: settingsLang.profileSettings)) {
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.goalSetting,
                onTap: {
                    router.navigate(to: .goal)
                }))
            .listRowInsets()
            
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.biologicalSex,
                value: settingsStore.biologicalSexText,
                chevronType: .upDown,
                onTap: { settingsStore.presentGenderPicker() }))
            .listRowInsets()
            
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.activityLevel,
                value: settingsStore.activityLevelText,
                chevronType: .upDown,
                onTap: { settingsStore.presentActivityPicker() }))
            .listRowInsets()
            
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.height,
                value: settingsStore.heightText,
                chevronType: .upDown,
                onTap: { settingsStore.presentHeightPicker() }
            ))
            .listRowInsets()
            
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.unitType,
                value: settingsStore.unitTypeText,
                chevronType: .upDown,
                onTap: { settingsStore.presentUnitPicker() }))
            .listRowInsets()
            
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.weightless,
                value: settingsStore.weightlessText,
                onTap: {
                    router.navigate(to: .weightless)
                }))
            .listRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }
    
    private func appSettingsSection() -> some View {
        Section(header: sectionHeader(title: settingsLang.appSettings)) {
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.notifications,
                value: settingsStore.notificationsOnText,
                chevronType: .upDown,
                onTap: { settingsStore.presentNotificationPicker() }))
            .listRowInsets()
            ActionListItemView(config: ActionListItemConfig(title: settingsStore.messagesTitleText, showDot: settingsStore.canShowFeedNotificationBadge, onTap: {
                router.navigate(to: .messages)
            }))
            .id(settingsStore.canShowFeedNotificationBadge)
            .listRowInsets()
            ActionListItemView(config: ActionListItemConfig(title: settingsLang.appPermissions, onTap: {
                router.navigate(to: .appPermissions)
            }))
            .listRowInsets()
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.appearance,
                value: settingsStore.appearanceModeText,
                chevronType: .upDown,
                onTap: { settingsStore.presentAppearancePicker() }))
            .listRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }
    
    private func supportSection() -> some View {
        Section(header:
                    SectionHeader(title: settingsLang.supportSettings)
        ) {
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.helpAndCustomerService,
                onTap: {
                    router.navigate(to: .help)
                }
            ))
            .listRowInsets()
            
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.privacyPolicy,
                onTap: {
                    settingsStore.openPrivacy()
                }
            ))
            .listRowInsets()
            
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.termsOfService,
                onTap: {
                    settingsStore.openTerms()
                }
            ))
            .listRowInsets()
            
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.greaterGoodsWebsite,
                onTap: {
                    settingsStore.openGreaterGoods()
                }
            ))
            .listRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }
    
    private func accountActionSection() -> some View {
        Section {
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.switchAccounts,
                onTap: {
                    router.navigate(to: .myAccounts)
                }
            ))
            .listRowInsets()
            
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.logOut,
                chevronType: .none,
                onTap: {
                    settingsStore.handleLogout()
                }
            ))
            .listRowInsets()
            
            if settingsStore.canShowLogOutAllItems {
                ActionListItemView(config: ActionListItemConfig(
                    title: settingsLang.logOutAllAccount,
                    chevronType: .none,
                    onTap: {
                        settingsStore.handleLogoutForAllAccounts()
                    }
                ))
                .listRowInsets()
            }
            
            ActionListItemView(config: ActionListItemConfig(
                title: settingsLang.deleteAccount,
                chevronType: .none,
                isDestructive: true,
                onTap: {
                    settingsStore.handleDeleteAccount()
                }
            ))
            .listRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
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
