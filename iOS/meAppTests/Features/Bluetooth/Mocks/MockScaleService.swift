import Combine
import Foundation
@testable import meApp

@MainActor
final class MockScaleService: ScaleServiceProtocol {
    @Published var scales: [Device] = []
    var scalesPublisher: AnyPublisher<[Device], Never> { $scales.eraseToAnyPublisher() }

    var updateAllScalesStatusError: Error?
    var syncDevicesError: Error?
    var createDeviceError: Error?
    var updateScalePreferenceError: Error?

    private(set) var updateConnectedDevicesCalls = 0
    private(set) var updateConnectedDeviceWifiStatusCalls = 0
    private(set) var updateConnectedDeviceWeightOnlyModeCalls = 0
    private(set) var syncDevicesCalls = 0
    private(set) var createDeviceCalls = 0
    private(set) var lastCreatedDevice: Device?

    func clearAllData() async {}
    func getDevices() async throws -> [Device] { scales }
    func getConnectedDevices() async -> [String: Any] { [:] }

    func updateConnectedDevices(device: Any, isConnected: Bool) async {
        updateConnectedDevicesCalls += 1
    }

    func updateConnectedDeviceWifiStatus(broadcastId: String, isConfigured: Bool) async {
        updateConnectedDeviceWifiStatusCalls += 1
    }

    func updateConnectedDeviceWeightOnlyMode(broadcastId: String, isWeightOnlyModeEnabledByOthers: Bool) async {
        updateConnectedDeviceWeightOnlyModeCalls += 1
    }

    func syncDevices(tempDevice: Device?) async throws {
        syncDevicesCalls += 1
        if let syncDevicesError { throw syncDevicesError }
    }

    func createDevice(_ device: Device, _ skipDuplicateCheck: Bool) async throws -> Device {
        createDeviceCalls += 1
        lastCreatedDevice = device
        if let createDeviceError { throw createDeviceError }
        return device
    }

    func editDevice(_ deviceId: String, properties: [String: Any]) async throws -> Device {
        throw UnexpectedCallError.methodCalled("editDevice")
    }

    func deleteDevice(_ deviceId: String, showToast: Bool) async throws {
        throw UnexpectedCallError.methodCalled("deleteDevice")
    }

    func updateScaleMeta(_ deviceId: String, metaData: DeviceMetaData) async throws {}
    func updateScalePreference(_ deviceId: String, _ preference: R4ScalePreference) async throws {
        if let updateScalePreferenceError { throw updateScalePreferenceError }
    }
    func updateScalePreference(_ deviceId: String, fromDTO dto: R4ScalePreferenceDTO) async throws {}

    func updateAllScalesStatus(_ scales: [Device]?) async throws {
        if let updateAllScalesStatusError { throw updateAllScalesStatusError }
    }

    func syncAllScalesWithRemote() async {}
    func pushLocalChangesToServer() async {}
    func getDevice(by deviceId: String) async throws -> Device? { scales.first { $0.id == deviceId } }
    func fetchAttachedPreference(by id: String) async -> R4ScalePreference? { nil }
    func fetchAttachedPreferenceSync(by id: String) -> R4ScalePreference? { nil }
}
