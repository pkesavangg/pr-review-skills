import Foundation
import GGBluetoothSwiftPackage
@testable import meApp

@MainActor
struct BluetoothScaleSetupStoreHarness {
    let store: BluetoothScaleSetupStore
    let notification: TestNotificationHelperService
    let permissions: MockPermissionsService
    let bluetooth: MockBluetoothService
    let account: MockAccountService
    let scaleService: MockScaleService
}

enum BluetoothScaleSetupStoreTestFixtures {
    @MainActor
    static func makeSUT(
        notification: TestNotificationHelperService? = nil,
        permissions: MockPermissionsService? = nil,
        bluetooth: MockBluetoothService? = nil,
        account: MockAccountService? = nil,
        scaleService: MockScaleService? = nil,
        pairingTimeoutNs: UInt64 = 40_000_000,
        stepTransitionDelayNs: UInt64 = 5_000_000
    ) -> BluetoothScaleSetupStoreHarness {
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

        let store = BluetoothScaleSetupStore(
            pairingTimeoutNs: pairingTimeoutNs,
            stepTransitionDelayNs: stepTransitionDelayNs
        )
        store.testWarmInjectedDependencies()

        return BluetoothScaleSetupStoreHarness(
            store: store,
            notification: notification,
            permissions: permissions,
            bluetooth: bluetooth,
            account: account,
            scaleService: scaleService
        )
    }

    @MainActor
    static func configureDefaultScale(_ store: BluetoothScaleSetupStore) {
        store.configure(with: "0375")
    }

    @MainActor
    static func makeAccount(
        id: String = "acct-1",
        email: String = "bluetooth-setup@example.com",
        firstName: String = "Lakshmi"
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

    static func makeBluetoothScale(
        id: String = "scale-1",
        accountId: String = "acct-1",
        userNumber: String = "1"
    ) -> Device {
        let scale = ScaleTestFixtures.makeDevice(
            id: id,
            accountId: accountId,
            displayName: "Bluetooth Scale",
            sku: "0375",
            deviceName: "BT Scale"
        )
        scale.userNumber = userNumber
        scale.bathScale = BathScale(scaleType: ScaleSourceType.bluetooth.rawValue, bodyComp: false)
        return scale
    }

    static func makeDiscoveryEvent(
        scale: Device = makeBluetoothScale(),
        setupType: ScaleSetupType = .bluetooth
    ) -> DeviceDiscoveryEvent {
        let fallback = SCALES.first ?? ScaleItemInfo(
            productName: "Bluetooth Smart Scale",
            sku: "0375",
            imgPath: AppAssets.scale0375,
            setupType: .bluetooth,
            bodyComp: false
        )

        let scaleInfo = SCALES.first { $0.setupType == setupType } ?? fallback
        return DeviceDiscoveryEvent(
            device: scale,
            deviceInfo: scaleInfo,
            protocolType: .A6,
            isNew: true
        )
    }

    static func makeDeviceInfo(
        serialNumber: String = "SERIAL-1",
        broadcastId: String = "A1B2",
        password: String = "00FF",
        macAddress: String = "AA:BB:CC:DD:EE:FF"
    ) -> DeviceInfo {
        DeviceInfo(
            serialNumber: serialNumber,
            deviceName: "Bluetooth Scale",
            broadcastId: broadcastId,
            broadcastIdString: broadcastId,
            password: password,
            macAddress: macAddress,
            identifier: serialNumber
        )
    }

    static func makeEntryNotification(accountId: String = "acct-1") -> EntryNotification {
        EntryNotification(from: BathScaleOperationDTO(
            accountId: accountId,
            bmr: nil,
            bmi: nil,
            bodyFat: nil,
            boneMass: nil,
            entryTimestamp: "2026-03-05T00:00:00.000Z",
            impedance: nil,
            metabolicAge: nil,
            muscleMass: nil,
            operationType: "CREATE",
            proteinPercent: nil,
            pulse: nil,
            serverTimestamp: nil,
            skeletalMusclePercent: nil,
            source: EntrySource.bluetooth.rawValue,
            subcutaneousFatPercent: nil,
            unit: "kg",
            visceralFatLevel: nil,
            water: nil,
            weight: 72.4
        ))
    }

    @MainActor
    static func waitUntil(
        timeoutNanoseconds: UInt64 = 1_000_000_000,
        pollNanoseconds: UInt64 = 20_000_000,
        condition: @escaping @MainActor () -> Bool
    ) async {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while !condition() && ContinuousClock.now < deadline {
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
    }
}
