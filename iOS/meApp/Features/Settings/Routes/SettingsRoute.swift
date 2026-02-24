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
    case scaleModes(scale: Device, isWeighOnlyModeEnabledByOthers: Bool = false)
    case displayMetrics(scale: Device, isWeighOnlyModeEnabledByOthers: Bool = false)
    case scaleNameScreen(scale: Device)
    case users(scale: Device, usersList: [DeviceUser])
    case wifi(scale: Device)
    case scaleBluetoothScreen(scale: Device)
    case scaleSettings(scale: Device, scaleType: ScaleType)  
    case addEditScales, integrations, goal, weightless, messages, appPermissions, help, myAccounts, wifiMacAddress(macAddress: String)

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
        case .scaleModes(let scale, let isWeighOnlyModeEnabledByOthers):
            ScaleModesScreen(scale: scale, isWeighOnlyModeEnabledByOthers: isWeighOnlyModeEnabledByOthers)
        case .displayMetrics(let scale, let isWeighOnlyModeEnabledByOthers):
            DisplayMetricsScreen(scale: scale, isWeighOnlyModeEnabledByOthers: isWeighOnlyModeEnabledByOthers)
        case .scaleNameScreen(let scale):
            ScaleNameScreen(scale: scale)
        case .users(let scale, let usersList):
            UsersScreen(scale: scale, usersList: usersList)
        case .scaleBluetoothScreen(let scale):
            ScaleBluetoothScreen(scale: scale)
        case .wifi(let scale):
            let sku = scale.sku ?? "default"
// swiftlint:disable:next line_length
            BtWifiScaleSetupScreen(sku: sku, discoveredScale: nil, discoveryEvent: nil, savedScale: scale, isReconnect: false, isDuplicated: false, isWifiSetupOnly: true)
        case .editProfile:
            EditProfileScreen()
        case .goal:
            GoalSettingScreen()
        case .weightless:
            WeightlessScreen()
        case .messages:
            IAMScreen()
        case .appPermissions:
            AppPermissionsScreen()
        case .help:
            HelpScreen()
        case .myAccounts:
            MyAccountsScreen()
        case .wifiMacAddress(let macAddress):
            WifiMacAddressScreen(macAddress: macAddress)
        }
    }
}
