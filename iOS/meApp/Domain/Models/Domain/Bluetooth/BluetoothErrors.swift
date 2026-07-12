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
    case startLiveMeasurementFailed(Error)
    case stopLiveMeasurementFailed(Error)
    case getDeviceLogsFailed(Error)
    case getScaleUserListFailed(Error)
    case getDeviceInfoFailed(Error)
    case getMeasurementLiveDataFailed(Error)
    case syncDevicesFailed(Error)
    case disconnectFailed(Error)
    case confirmPairFailed(Error)
    case updateWeightOnlyModeFailed(Error)
    case showWeightOnlyModeFailed(Error)
    case showUpdatesPendingAlertFailed(Error)
    case pairOperationFailed(Error)
    case deviceNotFound
    case invalidDeviceState
    case bluetoothUnavailable
    case permissionDenied
    case scanInProgress
    case deviceAlreadyConnected
    case deviceNotConnected

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
        case .pairOperationFailed(let error):
            return "Pairing operation failed: \(error.localizedDescription)"
        case .deviceNotFound:
            return "Device not found or no longer available"
        case .invalidDeviceState:
            return "Device is in an invalid state for this operation"
        case .bluetoothUnavailable:
            return "Bluetooth is not available or turned off"
        case .permissionDenied:
            return "Required permissions were denied"
        case .scanInProgress:
            return "Cannot perform operation while scan is in progress"
        case .deviceAlreadyConnected:
            return "Device is already connected"
        case .deviceNotConnected:
            return "Device is not connected"
        case .getDeviceLogsFailed:
            return "Get device logs failed"
        case .startLiveMeasurementFailed(let error):
            return "Start live measurement failed \(error.localizedDescription)"
        case .stopLiveMeasurementFailed(let error):
            return "Stop live measurement failed \(error.localizedDescription)"
        }
    }
}

extension BluetoothServiceError {
    /// Whether a failed connect/pair attempt is worth retrying once. Re-pairing the same
    /// monitor under a newly-selected user times out or throws a pairing error on the first
    /// attempt (the SDK still holds the previous user's session); that failed attempt tears
    /// the stale session down, so a second attempt succeeds. Non-transient failures —
    /// Bluetooth off, permission denied, invalid broadcast id, device not found, etc. — can't
    /// be fixed by a second connect, so they must fail fast instead of paying the retry delay.
    var isRetryablePairingFailure: Bool {
        switch self {
        case .timeout, .pairFailed, .pairOperationFailed, .confirmPairFailed:
            return true
        default:
            return false
        }
    }
}
