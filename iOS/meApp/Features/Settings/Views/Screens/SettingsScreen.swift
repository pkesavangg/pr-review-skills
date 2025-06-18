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
    @StateObject var settingsStore = SettingsStore()
    
    let settingsLang = SettingsStrings.self
    let commonLang = CommonStrings.self
    let labels = InputFieldLabels.self
    let appAssets = AppAssets.self
    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView<EmptyView, EmptyView>(title: settingsLang.title, canShowBorder: true)
            ZStack {
                theme.backgroundSecondary
                    .ignoresSafeArea()
                List {
                    supportSection()
                    accountActionSection()
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
            }
            .inAppBrowser(
                url: settingsStore.presentingBrowserURL,
                isPresented: settingsStore.isBrowserPresented
            )
        }
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
