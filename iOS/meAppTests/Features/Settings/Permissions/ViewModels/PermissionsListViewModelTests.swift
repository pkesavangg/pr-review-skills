import Combine
import Foundation
import GGBluetoothSwiftPackage
@testable import meApp
import Testing

/// `PermissionsListViewModel` maps the central `PermissionsService` snapshot onto published
/// flags. It also observes `NetworkMonitor.shared`, which is not injectable, so these tests
/// deliberately avoid asserting on the network/Wi-Fi-derived state and focus on the permission
/// mapping and the `handlePermission` forwarding path.
@Suite(.serialized)
@MainActor
struct PermissionsListViewModelTests {

    // swiftlint:disable:next large_tuple
    private func makeSUT() -> (
        sut: PermissionsListViewModel,
        permissions: MockPermissionsService,
        logger: MockLoggerService,
        wifi: MockWifiScaleService
    ) {
        TestDependencyContainer.reset()
        let permissions = MockPermissionsService()
        let logger = MockLoggerService()
        let wifi = MockWifiScaleService()
        DependencyContainer.shared.register(permissions)
        DependencyContainer.shared.register(permissions as PermissionsServiceProtocol)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)
        DependencyContainer.shared.register(wifi as WifiPairedDeviceServiceProtocol)
        return (PermissionsListViewModel(), permissions, logger, wifi)
    }

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 2_000_000_000,
        pollNanoseconds: UInt64 = 20_000_000,
        condition: @escaping @MainActor () -> Bool
    ) async -> Bool {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while !condition() && ContinuousClock.now < deadline {
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
        return condition()
    }

    // MARK: - Permission mapping

    @Test("emitting an all-granted snapshot enables every mapped permission flag")
    func allGrantedSnapshotEnablesFlags() async {
        let (sut, permissions, _, _) = makeSUT()

        permissions.emitPermissions([
            .BLUETOOTH: .ENABLED,
            .BLUETOOTH_SWITCH: .ENABLED,
            .LOCATION_SWITCH: .ENABLED,
            .LOCATION: .ENABLED,
            .CAMERA: .ENABLED,
            .NOTIFICATION: .ENABLED,
            .WIFI_SWITCH: .ENABLED
        ])

        let applied = await waitUntil { sut.bluetoothAuthorized }
        #expect(applied == true)
        #expect(sut.bluetoothPoweredOn == true)
        #expect(sut.locationServicesEnabled == true)
        #expect(sut.locationAuthorized == true)
        #expect(sut.cameraAuthorized == true)
        #expect(sut.notificationsEnabled == true)
        #expect(sut.wifiSwitchEnabled == true)
    }

    @Test("a disabled entry maps to a false flag")
    func disabledEntryMapsToFalse() async {
        let (sut, permissions, _, _) = makeSUT()

        permissions.emitPermissions([
            .BLUETOOTH: .ENABLED,
            .LOCATION: .DISABLED
        ])

        let applied = await waitUntil { sut.bluetoothAuthorized }
        #expect(applied == true)
        #expect(sut.locationAuthorized == false)
    }

    @Test("a nil snapshot resets all permission flags to false")
    func nilSnapshotResetsFlags() async {
        let (sut, permissions, _, _) = makeSUT()
        permissions.emitPermissions([.BLUETOOTH: .ENABLED, .CAMERA: .ENABLED])
        _ = await waitUntil { sut.bluetoothAuthorized }

        permissions.emitPermissions(nil)

        let reset = await waitUntil { sut.bluetoothAuthorized == false }
        #expect(reset == true)
        #expect(sut.cameraAuthorized == false)
        #expect(sut.notificationsEnabled == false)
        #expect(sut.locationAuthorized == false)
    }

    // MARK: - handlePermission

    @Test("handlePermission forwards the request to the permissions service")
    func handlePermissionForwardsToService() async {
        let (sut, permissions, _, _) = makeSUT()

        sut.handlePermission(.bluetooth)

        let forwarded = await waitUntil { permissions.handlePermissionCalls.contains(.bluetooth) }
        #expect(forwarded == true)
    }

    @Test("handlePermission forwards each distinct permission type to the service")
    func handlePermissionForwardsDistinctTypes() async {
        let (sut, permissions, _, _) = makeSUT()

        sut.handlePermission(.camera)
        sut.handlePermission(.notification)

        let forwarded = await waitUntil {
            permissions.handlePermissionCalls.contains(.camera) &&
            permissions.handlePermissionCalls.contains(.notification)
        }
        #expect(forwarded == true)
    }
}
