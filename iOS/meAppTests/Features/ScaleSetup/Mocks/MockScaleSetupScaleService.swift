//
//  MockScaleSetupScaleService.swift
//  meAppTests
//

import Foundation
import Combine
@testable import meApp

@MainActor
final class MockScaleSetupScaleService: ScaleServiceProtocol {

    // MARK: - scales
    var scales: [Device] = []
    var scalesPublisher: AnyPublisher<[Device], Never> {
        Just(scales).eraseToAnyPublisher()
    }

    // MARK: - getDevices
    var getDevicesResult: [Device] = []
    var getDevicesError: Error?
    var getDevicesCallCount = 0

    func getDevices() async throws -> [Device] {
        getDevicesCallCount += 1
        if let error = getDevicesError { throw error }
        return getDevicesResult
    }

    // MARK: - getConnectedDevices
    func getConnectedDevices() async -> [String: Any] { [:] }

    // MARK: - updateConnectedDevices
    func updateConnectedDevices(device: Any, isConnected: Bool) async {}

    // MARK: - updateConnectedDeviceWifiStatus
    func updateConnectedDeviceWifiStatus(broadcastId: String, isConfigured: Bool) async {}

    // MARK: - updateConnectedDeviceWeightOnlyMode
    func updateConnectedDeviceWeightOnlyMode(broadcastId: String, isWeightOnlyModeEnabledByOthers: Bool) async {}

    // MARK: - syncDevices
    func syncDevices(tempDevice: Device?) async throws {}

    // MARK: - createDevice
    var createDeviceResult: Device?
    var createDeviceError: Error?
    var createDeviceCallCount = 0
    var lastCreatedDevice: Device?

    func createDevice(_ device: Device, _ skipDuplicateCheck: Bool) async throws -> Device {
        createDeviceCallCount += 1
        lastCreatedDevice = device
        if let error = createDeviceError { throw error }
        return createDeviceResult ?? device
    }

    // MARK: - editDevice
    var editDeviceError: Error?

    func editDevice(_ deviceId: String, properties: [String: Any]) async throws -> Device {
        if let error = editDeviceError { throw error }
        return scales.first { $0.id == deviceId } ?? Device(id: deviceId, accountId: "")
    }

    // MARK: - deleteDevice
    var deleteDeviceCallCount = 0
    var deleteDeviceError: Error?

    func deleteDevice(_ deviceId: String, showToast: Bool) async throws {
        deleteDeviceCallCount += 1
        if let error = deleteDeviceError { throw error }
    }

    // MARK: - syncAllScalesWithRemote
    var syncAllScalesCallCount = 0

    func syncAllScalesWithRemote() async {
        syncAllScalesCallCount += 1
    }

    // MARK: - createBluetoothScale
    var createBluetoothScaleResult: Device?
    var createBluetoothScaleError: Error?
    var createBluetoothScaleCallCount = 0

    func createBluetoothScale(device: Device, sku: String?, userNumber: String, accountId: String, deviceMetadata: DeviceMetaData?, skipDuplicateCheck: Bool) async throws -> Device {
        createBluetoothScaleCallCount += 1
        if let error = createBluetoothScaleError { throw error }
        return createBluetoothScaleResult ?? device
    }

    // MARK: - createA6Scale
    var createA6ScaleResult: Device?
    var createA6ScaleError: Error?
    var createA6ScaleCallCount = 0

    func createA6Scale(device: Device, sku: String?, accountId: String, deviceMetadata: DeviceMetaData?, skipDuplicateCheck: Bool) async throws -> Device {
        createA6ScaleCallCount += 1
        if let error = createA6ScaleError { throw error }
        return createA6ScaleResult ?? device
    }

    // MARK: - updateScaleMeta
    func updateScaleMeta(_ deviceId: String, metaData: DeviceMetaData) async throws {}

    // MARK: - updateScalePreference
    func updateScalePreference(_ deviceId: String, _ preference: R4ScalePreference) async throws {}
    func updateScalePreference(_ deviceId: String, fromDTO dto: R4ScalePreferenceDTO) async throws {}

    // MARK: - updateAllScalesStatus
    func updateAllScalesStatus(_ scales: [Device]?) async throws {}

    // MARK: - fetchAttachedPreference
    func fetchAttachedPreference(by id: String) async -> R4ScalePreference? { nil }
    func fetchAttachedPreferenceSync(by id: String) -> R4ScalePreference? { nil }
}
