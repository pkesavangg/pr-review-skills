
//
//  BluetoothErrors.swift
//  meApp
//
//  Created by Barath Chittibabu on 02/07/25.
//
import Foundation
// MARK: - Error
enum BluetoothServiceError: Error, LocalizedError {
    case notImplemented
    case invalidBroadcastId
    case noActiveAccount
    case noProfileInfo
    case scanFailed(Error)
    case stopFailed(Error)
    case clearDevicesFailed(Error)
    case pairFailed(Error)
    case resyncFailed(Error)
    case pauseFailed(Error)
    case resumeFailed(Error)
    case scanForPairingFailed(Error)
    case syncFailed(Error)
    case getWifiListFailed(Error)
    case wifiSetupFailed(Error)
    case cancelWifiFailed(Error)
    case getConnectedWifiSSIDFailed(Error)
    case getWifiMacAddressFailed(Error)
    case timeout
    case updateSettingFailed(Error)
    case firmwareUpdateFailed(Error)
    case clearDataFailed(Error)
    case updateProfileFailed(Error)
    case getScaleUserListFailed(Error)
    case getDeviceInfoFailed(Error)
    case getMeasurementLiveDataFailed(Error)
    case syncDevicesFailed(Error)
    case disconnectFailed(Error)
    case confirmPairFailed(Error)
    case updateWeightOnlyModeFailed(Error)
    case showWeightOnlyModeFailed(Error)
    case showUpdatesPendingAlertFailed(Error)

    var errorDescription: String? {
        switch self {
        case .notImplemented:
            return "Feature not yet implemented"
        case .invalidBroadcastId:
            return "Invalid broadcast ID"
        case .noActiveAccount:
            return "No active account available"
        case .noProfileInfo:
            return "No profile information available"
        case .scanFailed(let error):
            return "Scan failed: \(error.localizedDescription)"
        case .stopFailed(let error):
            return "Stop failed: \(error.localizedDescription)"
        case .clearDevicesFailed(let error):
            return "Clear devices failed: \(error.localizedDescription)"
        case .pairFailed(let error):
            return "Pairing failed: \(error.localizedDescription)"
        case .resyncFailed(let error):
            return "Resync failed: \(error.localizedDescription)"
        case .pauseFailed(let error):
            return "Pause scan failed: \(error.localizedDescription)"
        case .resumeFailed(let error):
            return "Resume scan failed: \(error.localizedDescription)"
        case .scanForPairingFailed(let error):
            return "Scan for pairing failed: \(error.localizedDescription)"
        case .syncFailed(let error):
            return "Sync failed: \(error.localizedDescription)"
        case .getWifiListFailed(let error):
            return "Get WiFi list failed: \(error.localizedDescription)"
        case .wifiSetupFailed(let error):
            return "WiFi setup failed: \(error.localizedDescription)"
        case .cancelWifiFailed(let error):
            return "Cancel WiFi failed: \(error.localizedDescription)"
        case .getConnectedWifiSSIDFailed(let error):
            return "Get connected WiFi SSID failed: \(error.localizedDescription)"
        case .getWifiMacAddressFailed(let error):
            return "Get WiFi MAC address failed: \(error.localizedDescription)"
        case .timeout:
            return "Operation timed out"
        case .updateSettingFailed(let error):
            return "Update setting failed: \(error.localizedDescription)"
        case .firmwareUpdateFailed(let error):
            return "Firmware update failed: \(error.localizedDescription)"
        case .clearDataFailed(let error):
            return "Clear data failed: \(error.localizedDescription)"
        case .updateProfileFailed(let error):
            return "Update profile failed: \(error.localizedDescription)"
        case .getScaleUserListFailed(let error):
            return "Get scale user list failed: \(error.localizedDescription)"
        case .getDeviceInfoFailed(let error):
            return "Get device info failed: \(error.localizedDescription)"
        case .getMeasurementLiveDataFailed(let error):
            return "Get measurement live data failed: \(error.localizedDescription)"
        case .updateWeightOnlyModeFailed(let error):
            return "Update weight only mode failed: \(error.localizedDescription)"
        case .syncDevicesFailed(let error):
            return "Sync devices failed: \(error.localizedDescription)"
        case .disconnectFailed(let error):
            return "Disconnect failed: \(error.localizedDescription)"
        case .confirmPairFailed(let error):
            return "Confirm pair failed: \(error.localizedDescription)"
        case .showWeightOnlyModeFailed(let error):
            return "Show weight only mode failed: \(error.localizedDescription)"
        case .showUpdatesPendingAlertFailed(let error):
            return "Show updates pending alert failed: \(error.localizedDescription)"
        }
    }
}
