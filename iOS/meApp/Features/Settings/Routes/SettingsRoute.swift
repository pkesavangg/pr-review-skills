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
    case displayMetrics
    case scaleNameScreen(scaleName: String)
    case users
    case scaleBluetoothScreen(scale: Device)
    case wifi
    case wifiCredentials(wifiName: String)
    
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
        case .displayMetrics:
            DisplayMetricsScreen()
        case .scaleNameScreen(let scaleName):
            ScaleNameScreen(scaleName: scaleName)
        case .users:
            UsersScreen()
        case .scaleBluetoothScreen(let scale):
            ScaleBluetoothScreen(scale: scale)
        case .wifi:
            WifiScreen()
        case .wifiCredentials(let wifiName):
            WifiCredentialsView(wifiName: wifiName)
        }
    }
}
