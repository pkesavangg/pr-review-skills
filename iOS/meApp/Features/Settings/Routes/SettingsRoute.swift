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
    case scaleModes(scale: Device)
    case displayMetrics
    case scaleNameScreen(scale: Device)
    case users
    case wifi
    case wifiCredentials(wifiName: String)    
    case scaleBluetoothScreen(scale: Device)  
    case scaleSettings(scale: Device, scaleType: ScaleType)  
    case addEditScales, integrations, goal, weightless, messages, appPermissions, help, myAccounts, wifiMacAddress(scale: Device)

    var body: some View {
        switch self {
        case .integrations:
            IntegrationsScreen()
        case .changePassword:
            ChangePasswordScreen()
        case .addEditScales:
            MyScalesScreen()
        case .scaleSettings(let scale, let scaleType):
            ScaleSettingsScreen(scale: scale, scaleType: scaleType)
        case .scaleModes(let scale):
            ScaleModesScreen(scale: scale)
        case .displayMetrics:
            DisplayMetricsScreen()
        case .scaleNameScreen(let scale):
            ScaleNameScreen(scale: scale)
        case .users:
            UsersScreen()
        case .scaleBluetoothScreen(let scale):
            ScaleBluetoothScreen(scale: scale)
        case .wifi:
            WifiScreen()
        case .wifiCredentials(let wifiName):
            WifiCredentialsView(wifiName: wifiName)
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
        case .wifiMacAddress(let scale):
            WifiMacAddressScreen(scale: scale)
        }
    }
}

