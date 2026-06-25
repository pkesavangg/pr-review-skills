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
    case deviceModes(scale: Device, isWeighOnlyModeEnabledByOthers: Bool = false)
    case displayMetrics(scale: Device, isWeighOnlyModeEnabledByOthers: Bool = false)
    case deviceNameScreen(scale: Device)
    case users(scale: Device, usersList: [DeviceUser])
    case wifi(scale: Device)
    case deviceBluetoothScreen(scale: Device)
    case deviceSettings(scale: Device, scaleType: DeviceModelType)
    case bpmDeviceSettings(device: Device)
    case addEditScales, integrations, goal, weightless, messages, appPermissions, help, myAccounts, myKids, addBaby, wifiMacAddress(macAddress: String)

    var body: some View {
        switch self {
        case .integrations:
            IntegrationsScreen()
        case .changePassword:
            ChangePasswordScreen()
        case .addEditScales:
            MyDevicesScreen()
        case .deviceSettings(let scale, let scaleType):
            DeviceSettingsScreen(scale: scale, scaleType: scaleType)
        case .bpmDeviceSettings(let device):
            BpmDeviceSettingsScreen(device: device)
        case .deviceModes(let scale, let isWeighOnlyModeEnabledByOthers):
            DeviceModesScreen(scale: scale, isWeighOnlyModeEnabledByOthers: isWeighOnlyModeEnabledByOthers)
        case .displayMetrics(let scale, let isWeighOnlyModeEnabledByOthers):
            DisplayMetricsScreen(scale: scale, isWeighOnlyModeEnabledByOthers: isWeighOnlyModeEnabledByOthers)
        case .deviceNameScreen(let scale):
            DeviceNameScreen(scale: scale)
        case .users(let scale, let usersList):
            UsersScreen(scale: scale, usersList: usersList)
        case .deviceBluetoothScreen(let scale):
            DeviceBluetoothScreen(scale: scale)
        case .wifi(let scale):
            let sku = scale.sku ?? "default"
            BtWifiScaleSetupScreen(
                sku: sku,
                discoveredScale: nil,
                discoveryEvent: nil,
                savedScale: scale,
                isReconnect: false,
                isDuplicated: false,
                isWifiSetupOnly: true
            )
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
        case .myKids:
            MyKidsScreen()
        case .addBaby:
            MyKidsAddBabyScreen()
        case .wifiMacAddress(let macAddress):
            WifiMacAddressScreen(macAddress: macAddress)
        }
    }
}
