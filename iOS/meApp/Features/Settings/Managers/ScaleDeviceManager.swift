import Foundation
import SwiftUI

/// Manages all device-specific operations and state
@MainActor
class ScaleDeviceManager: ObservableObject {

    // MARK: - Dependencies
    @Injector private var scaleService: ScaleService
    @Injector private var bluetoothService: BluetoothService
    @Injector private var logger: LoggerService

    // MARK: - Published Properties
    @Published var state: ScaleDeviceState

    // MARK: - Initialization
    init(initialState: ScaleDeviceState = ScaleDeviceState()) {
        self.state = initialState
    }

    // MARK: - Device Loading
    func loadScale(_ scale: Device) async {
        state.scale = scale
        state.isBluetoothScale = scale.deviceType == "bluetooth"
        state.isDeviceConnected = scale.isConnected ?? false
        state.scaleTypeValue = determineScaleType(for: scale)
        state.skuValue = scale.sku ?? ""
        
        await getDeviceInfo()
        await getConnectedWifiSSID()
        await refreshConnectionStatus()
        
        if shouldFetchWifiMacAddress(for: scale) {
            await fetchWifiMacAddress()
        }
    }

    func getDeviceInfo() async {
        guard let scale = state.scale else { return }
        
        do {
            if let device = try await scaleService.getDevices().first(where: { $0.id == scale.id }) {
                state.deviceInfo = device.metaData
                state.macAddress = device.mac
                state.firmwareVersion = device.metaData?.firmwareRevision
            }
        } catch {
            logger.log(level: .error, tag: "ScaleDeviceManager", message: "Failed to get device info: \(error)")
        }
    }

    func getConnectedWifiSSID() async {
        guard let scale = state.scale else { return }
        
        if ScaleTypeHelper.determineScaleType(for: scale) == .bluetoothR4 && scale.isConnected == true {
            do {
                let result = await bluetoothService.getConnectedWifiSSID(broadcastId: scale.broadcastIdString ?? "")
                switch result {
                case .success(let ssid):
                    state.connectedWifiSSID = ssid.isEmpty ? nil : ssid
                case .failure(let error):
                    logger.log(level: .error, tag: "ScaleDeviceManager", message: "Failed to get WiFi SSID: \(error)")
                    state.connectedWifiSSID = nil
                }
            }
        } else {
            state.connectedWifiSSID = nil
        }
    }

    func refreshConnectionStatus() async {
        guard let scale = state.scale else { return }
        
        do {
            let devices = try await scaleService.getDevices()
            if let updatedScale = devices.first(where: { $0.id == scale.id }) {
                state.scale = updatedScale
                state.isDeviceConnected = updatedScale.isConnected ?? false
            }
        } catch {
            logger.log(level: .error, tag: "ScaleDeviceManager", message: "Failed to refresh connection: \(error)")
        }
    }

    func fetchWifiMacAddress() async {
        guard let scale = state.scale else { return }
        
        do {
            let result = await bluetoothService.getWifiMacAddress(for: scale)
            switch result {
            case .success(let macAddress):
                state.scale?.wifiMac = macAddress
            case .failure(let error):
                logger.log(level: .error, tag: "ScaleDeviceManager", message: "Failed to get WiFi MAC: \(error)")
            }
        }
    }

    // MARK: - Computed Properties
    var wifiValue: String {
        guard let scale = state.scale else { return "" }
        
        if ScaleTypeHelper.determineScaleType(for: scale) == .bluetoothR4 && scale.isConnected == true {
            if let connectedSSID = state.connectedWifiSSID, !connectedSSID.isEmpty {
                return connectedSSID
            }
            return ""
        }
        return ""
    }

    var datePairedValue: String {
        guard let scale = state.scale, let createdAt = scale.createdAt else { return "" }
        return DateTimeTools.getFormattedDate(createdAt)
    }

    var bluetoothValue: String {
        guard let scale = state.scale else { return ScaleBluetoothStrings.notConnected }
        return scale.isConnected == true ? ScaleBluetoothStrings.connected : ScaleBluetoothStrings.notConnected
    }

    var wifiMacAddressValue: String {
        guard let scale = state.scale, scale.isConnected == true else { return "" }
        return scale.wifiMac ?? ""
    }

    // MARK: - Helper Methods
    private func determineScaleType(for scale: Device) -> String {
        return ScaleTypeHelper.determineScaleTypeString(for: scale)
    }

    func determineConnectionStatus(for scale: Device) -> ScaleConnectionStatus {
        let scaleType = ScaleTypeHelper.determineScaleType(for: scale)
        
        if scaleType == .appsync {
            return .noStatus
        }
        
        if scaleType == .bluetoothR4 && scale.isConnected == true {
            let isWifiConfigured = scale.isWifiConfigured == true
            let isInWeightOnlyMode = isWeightOnlyModeEnabledByOthers(for: scale)
            
            if !isWifiConfigured && !isInWeightOnlyMode {
                return .setupIncomplete
            }
        }
        
        return scale.isConnected == true ? .connected : .notConnected
    }

    private func isWeightOnlyModeEnabledByOthers(for scale: Device) -> Bool {
        if let r4Preference = scale.r4ScalePreference {
            return !r4Preference.shouldMeasureImpedance
        }
        return false
    }

    func shouldFetchWifiMacAddress(for scale: Device) -> Bool {
        guard scale.isConnected == true else { return false }
        let isR4Scale = ScaleTypeHelper.determineScaleType(for: scale) == .bluetoothR4
        return isR4Scale
    }
} 