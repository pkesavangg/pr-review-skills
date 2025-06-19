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
    }
    
    private func profileHeader() -> some View {
        VStack(spacing: .spacingXS) {
            InitialIconView(
                character: settingsStore.profileInitial,
                size: 41,
                style: .fill
            )
            Text(settingsStore.profileName)
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)
            Text(settingsStore.profileEmail)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
            
            ButtonView(
                text: CommonStrings.edit,
                type: .primary,
                size: .regular,
                isDisabled: false,
                action: {
                    tabViewModel.showTabBar = false
                    router.navigate(to: .editProfile)
                }
            )
            .padding(.top, .spacingSM)
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
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.exportData))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.changePassword,
                                                       onTap: {
                                                           tabViewModel.showTabBar = false
                                                           router.navigate(to: .changePassword)
                                                       }))
                .settingsRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    
    private func profileSettingsSection() -> some View {
        Section(header: sectionHeader(title: settingsLang.profileSettings)) {
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.goalSetting))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.biologicalSex, value: settingsStore.biologicalSexText))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.activityLevel, value: settingsStore.activityLevelText))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.height, value: settingsStore.heightText))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.unitType, value: settingsStore.unitTypeText))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.weightless, value: settingsStore.weightlessText))
                .settingsRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    
    private func appSettingsSection() -> some View {
        Section(header: sectionHeader(title: settingsLang.appSettings)) {
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.notifications, value: settingsStore.notificationsOnText))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.messages))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.streaks, value: settingsStore.streaksOnText))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.appPermissions))
                .settingsRowInsets()
            SettingsListItem(config: SettingsItemConfig(title: settingsLang.appearance, value: settingsStore.appearanceModeText))
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
                canShowChevron: false,
                onTap: {
                    settingsStore.handleLogout()
                }
            ))
            .settingsRowInsets()
            
            SettingsListItem(config: SettingsItemConfig(
                title: settingsLang.deleteAccount,
                canShowChevron: false,
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
