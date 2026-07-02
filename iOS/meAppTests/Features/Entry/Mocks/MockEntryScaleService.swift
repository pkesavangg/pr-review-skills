import Combine
import Foundation
@testable import meApp

@MainActor
final class MockEntryScaleService: ScaleServiceProtocol {

    // MARK: - scalesPublisher
    private let scalesSubject = CurrentValueSubject<[Device], Never>([])

    var scalesPublisher: AnyPublisher<[Device], Never> {
        scalesSubject.eraseToAnyPublisher()
    }

    var scales: [Device] { scalesSubject.value }

    func sendScales(_ devices: [Device]) {
        scalesSubject.send(devices)
    }

    // MARK: - DeviceServiceProtocol stubs
    func getDevices() async throws -> [DeviceSnapshot] { [] }
    func getConnectedDevices() async -> [String: Any] { [:] }
    func updateConnectedDevices(device: Any, isConnected: Bool) async {}
    func updateConnectedDeviceWifiStatus(broadcastId: String, isConfigured: Bool) async {}
    func syncDevices(tempDevice: Device?) async throws {}
    func createDevice(_ device: Device, _ skipDuplicateCheck: Bool) async throws -> Device { device }
    func editDevice(_ deviceId: String, properties: [String: Any]) async throws -> Device {
        throw NSError(domain: "mock", code: 0)
    }
    func deleteDevice(_ deviceId: String, showToast: Bool) async throws {}

    // MARK: - ScaleServiceProtocol stubs
    func updateScaleMeta(_ deviceId: String, metaData: DeviceMetaData) async throws {}
    func updateScalePreference(_ deviceId: String, _ preference: R4ScalePreference) async throws {}
    func updateScalePreference(_ deviceId: String, fromDTO dto: R4ScalePreferenceDTO) async throws {}
    func updateAllScalesStatus(_ scales: [Device]?) async throws {}
    func updateConnectedDeviceWeightOnlyMode(broadcastId: String, isWeightOnlyModeEnabledByOthers: Bool) async {}
    func fetchAttachedPreference(by id: String) async -> R4ScalePreference? { nil }
    func fetchAttachedPreferenceSync(by id: String) -> R4ScalePreference? { nil }
    func syncAllScalesWithRemote() async {}
    // swiftlint:disable:next function_parameter_count
    func createBluetoothScale(
        device: Device,
        sku: String?,
        userNumber: String,
        accountId: String,
        deviceMetadata: DeviceMetaData?,
        skipDuplicateCheck: Bool
    ) async throws -> Device { device }
    func createA6Scale(
        device: Device,
        sku: String?,
        accountId: String,
        deviceMetadata: DeviceMetaData?,
        skipDuplicateCheck: Bool
    ) async throws -> Device { device }
}
