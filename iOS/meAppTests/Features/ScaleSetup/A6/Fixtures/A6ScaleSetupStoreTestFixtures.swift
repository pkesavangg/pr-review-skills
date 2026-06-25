import Foundation
import GGBluetoothSwiftPackage
@testable import meApp

@MainActor
struct A6ScaleSetupStoreHarness {
    let store: A6ScaleSetupStore
    let notification: TestNotificationHelperService
    let permissions: MockPermissionsService
    let bluetooth: MockBluetoothService
    let account: MockAccountService
    let scaleService: MockScaleService
}

enum A6ScaleSetupStoreTestFixtures {
    @MainActor
    static func makeSUT(
        notification: TestNotificationHelperService? = nil,
        permissions: MockPermissionsService? = nil,
        bluetooth: MockBluetoothService? = nil,
        account: MockAccountService? = nil,
        scaleService: MockScaleService? = nil,
        pairingTimeoutNs: UInt64 = 40_000_000,
        connectionTransitionDelayNs: UInt64 = 5_000_000
    ) -> A6ScaleSetupStoreHarness {
        let notification = notification ?? TestNotificationHelperService()
        let permissions = permissions ?? MockPermissionsService()
        let bluetooth = bluetooth ?? MockBluetoothService()
        let account = account ?? MockAccountService()
        let scaleService = scaleService ?? MockScaleService()

        if account.activeAccount == nil {
            let active = makeAccount()
            account.seedAccounts([active], active: active)
        }

        if permissions.permissions == nil {
            permissions.setPermissions(enabledPermissions())
        }

        DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
        DependencyContainer.shared.register(permissions as PermissionsServiceProtocol)
        DependencyContainer.shared.register(bluetooth as BluetoothServiceProtocol)
        DependencyContainer.shared.register(account as AccountServiceProtocol)
        DependencyContainer.shared.register(scaleService as PairedDeviceServiceProtocol)

        let store = A6ScaleSetupStore(
            pairingTimeoutNs: pairingTimeoutNs,
            connectionTransitionDelayNs: connectionTransitionDelayNs
        )
        store.testWarmInjectedDependencies()

        return A6ScaleSetupStoreHarness(
            store: store,
            notification: notification,
            permissions: permissions,
            bluetooth: bluetooth,
            account: account,
            scaleService: scaleService
        )
    }

    @MainActor
    static func configureDefaultScale(_ store: A6ScaleSetupStore) {
        store.configure(with: "0022")
    }

    @MainActor
    static func makeAccount(
        id: String = "acct-1",
        email: String = "a6-setup@example.com",
        firstName: String = "Lakshmi"
    ) -> AccountSnapshot {
        AccountTestFixtures.makeAccountSnapshot(id: id, email: email, firstName: firstName, isActiveAccount: true)
    }

    static func enabledPermissions() -> [GGPermissionType: GGPermissionState] {
        [
            .BLUETOOTH: .ENABLED,
            .BLUETOOTH_SWITCH: .ENABLED
        ]
    }

    static func disabledPermissions() -> [GGPermissionType: GGPermissionState] {
        [
            .BLUETOOTH: .DISABLED,
            .BLUETOOTH_SWITCH: .DISABLED
        ]
    }

    static func makeA6Device(
        id: String = "a6-scale-1",
        accountId: String = "acct-1"
    ) -> Device {
        let scale = ScaleTestFixtures.makeDevice(
            id: id,
            accountId: accountId,
            displayName: "A6 Scale",
            sku: "0022",
            deviceName: "LCBT Scale"
        )
        scale.bathScale = BathScale(scaleType: DeviceSourceType.bluetooth.rawValue, bodyComp: false)
        return scale
    }

    static func makeDiscoveryEvent(
        scale: Device? = nil,
        setupType: DeviceSetupType = .lcbt,
        isNew: Bool = true
    ) -> DeviceDiscoveryEvent {
        let device = scale ?? makeA6Device()
        let fallback = SCALES.first ?? DeviceItemInfo(
            productName: "A6 Smart Scale",
            sku: "0022",
            imgPath: AppAssets.scale0375,
            setupType: .lcbt,
            bodyComp: false
        )
        let scaleInfo = SCALES.first { $0.setupType == setupType } ?? fallback
        return DeviceDiscoveryEvent(
            device: device.toSnapshot(),
            deviceInfo: scaleInfo,
            protocolType: .A6,
            isNew: isNew
        )
    }

    @MainActor
    static func waitUntil(
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
}
