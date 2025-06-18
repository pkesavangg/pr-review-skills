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
                    accountActionSection()
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
            }
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
}

#Preview {
    SettingsScreen()
}
