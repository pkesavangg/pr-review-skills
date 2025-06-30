//
//  SettingsRoute.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 19/06/25.
//


import SwiftUI

// MARK: - Settings Route
enum SettingsRoute: Routable {
    case addEditScales, integrations, changePassword, editProfile, goal, weightless, messages, appPermissions, help, myAccounts

    var body: some View {
        switch self {
        case .integrations:
            IntegrationsScreen()
        case .changePassword:
            ChangePasswordScreen()
        case .addEditScales:
            MyScalesScreen()
        case .editProfile:
            EditProfileScreen()
        case .goal:
            GoalSettingScreen()
        case .weightless:
            WeightlessScreen()
        case .messages:
            EmptyView() // TODO: Implement MessagesScreen
        case .appPermissions:
            AppPermissionsScreen()
        case .help:
            HelpScreen()
        case .myAccounts:
            MyAccountsScreen()
        }
    }
}

