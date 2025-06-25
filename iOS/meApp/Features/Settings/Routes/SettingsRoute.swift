//
//  SettingsRoute.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 19/06/25.
//


import SwiftUI

// MARK: - Settings Route
enum SettingsRoute: Routable {
    case editProfile
    case changePassword
    case addAndEditScales
    case scaleSettings(scale: Device, scaleType: ScaleType)
    case scaleModes
    
    var body: some View {
        switch self {
        case .editProfile:
            EditProfileScreen()
        case .changePassword:
            ChangePasswordScreen()
        case .addAndEditScales:
            MyScalesScreen()
        case .scaleSettings(let scale, let scaleType):
            ScaleSettingsScreen(scale: scale, scaleType: scaleType)
        case .scaleModes:
            ScaleModesScreen()
        }
    }
}
