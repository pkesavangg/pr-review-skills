import Foundation
import GGBluetoothSwiftPackage
@testable import meApp

@MainActor
struct BpmSetupStoreHarness {
    let store: BpmSetupStore
    let notification: TestNotificationHelperService
    let permissions: MockPermissionsService
    let bluetooth: MockBluetoothService
    let account: MockAccountService
    let scaleService: MockScaleService
}

enum BpmSetupStoreTestFixtures {
    @MainActor
    static func makeSUT(
        notification: TestNotificationHelperService? = nil,
        permissions: MockPermissionsService? = nil,
        bluetooth: MockBluetoothService? = nil,
        account: MockAccountService? = nil,
        scaleService: MockScaleService? = nil,
        scanTimeoutNs: UInt64 = 40_000_000,
        stepTransitionDelayNs: UInt64 = 5_000_000
    ) -> BpmSetupStoreHarness {
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
        DependencyContainer.shared.register(scaleService as ScaleServiceProtocol)

        let store = BpmSetupStore(
            scanTimeoutNs: scanTimeoutNs,
            stepTransitionDelayNs: stepTransitionDelayNs
        )
        store.testWarmInjectedDependencies()

        return BpmSetupStoreHarness(
            store: store,
            notification: notification,
            permissions: permissions,
            bluetooth: bluetooth,
            account: account,
            scaleService: scaleService
        )
    }

    @MainActor
    static func configureA3Bpm(_ store: BpmSetupStore) {
        store.configure(with: "0603")
    }

    @MainActor
    static func configureA6Bpm(_ store: BpmSetupStore) {
        store.configure(with: "0663")
    }

    @MainActor
    static func makeAccount(
        id: String = "acct-1",
        email: String = "bpm-setup@example.com",
        firstName: String = "Test"
    ) -> Account {
        AccountTestFixtures.makeAccountModel(id: id, email: email, firstName: firstName, isActive: true)
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

    static func makeBpmDevice(
        id: String = "bpm-1",
        accountId: String = "acct-1"
    ) -> Device {
        ScaleTestFixtures.makeDevice(
            id: id,
            accountId: accountId,
            displayName: "Blood Pressure Monitor",
            sku: "0603",
            deviceName: "BPM Device"
        )
    }

    static func makeBpmDiscoveryEvent(
        device: Device? = nil,
        setupType: ScaleSetupType = .bpm
    ) -> DeviceDiscoveryEvent {
        let bpmDevice = device ?? makeBpmDevice()
        let info = ScaleItemInfo(
            productName: setupType == .bpm ? "Blood Pressure Monitor" : "Bluetooth Scale",
            sku: setupType == .bpm ? "0603" : "0375",
            imgPath: setupType == .bpm ? AppAssets.bpm0603 : AppAssets.scale0375,
            setupType: setupType,
            bodyComp: false
        )
        return DeviceDiscoveryEvent(
            device: bpmDevice,
            deviceInfo: info,
            protocolType: .A6,
            isNew: true
        )
    }

    static func makeBpmMeasurement(
        systolic: Int = 120,
        diastolic: Int = 80,
        pulse: Int = 72
    ) -> BpmMeasurement {
        BpmMeasurement(
            systolic: systolic,
            diastolic: diastolic,
            pulse: pulse,
            timestamp: Date()
        )
    }

    /// Returns the actual array index of a step within the store's configured steps.
    @MainActor
    static func stepIndex(_ step: BpmSetupStep, in store: BpmSetupStore) -> Int {
        store.steps.firstIndex(of: step)!
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
