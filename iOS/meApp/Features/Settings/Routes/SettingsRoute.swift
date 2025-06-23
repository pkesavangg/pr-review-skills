//
//  SettingsRoute.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 19/06/25.
//


import SwiftUI

// MARK: - Settings Route
enum SettingsRoute: Routable {
    case editProfile, changePassword, weightless, goal, appPermissions, help
    
    var body: some View {
        switch self {
        case .editProfile:
            EditProfileScreen()
        case .changePassword:
            ChangePasswordScreen()
        case .weightless:
            WeightlessScreen()
        case  .goal:
            GoalSettingScreen()
        case  .appPermissions:
            EmptyView()
        case  .help:
            EmptyView()
        }
    }
}
