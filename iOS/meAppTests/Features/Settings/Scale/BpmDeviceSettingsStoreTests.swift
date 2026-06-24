import Combine
import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct BpmDeviceSettingsStoreTests {

    // MARK: - Helpers

    private func makeSUT(
        device: Device? = nil,
        notification: MockNotificationHelperService? = nil,
        scaleService: MockScaleService? = nil,
        bluetooth: MockBluetoothService? = nil,
        permissions: MockPermissionsService? = nil
    ) -> (
        store: BpmDeviceSettingsStore,
        notification: MockNotificationHelperService,
        scaleService: MockScaleService,
        bluetooth: MockBluetoothService,
        permissions: MockPermissionsService
    ) {
        let notification = notification ?? MockNotificationHelperService()
        let scaleService = scaleService ?? MockScaleService()
        let bluetooth = bluetooth ?? MockBluetoothService()
        let permissions = permissions ?? {
            let p = MockPermissionsService()
            p.permissions = [
                .BLUETOOTH: .ENABLED,
                .BLUETOOTH_SWITCH: .ENABLED
            ]
            return p
        }()
        let logger = MockLoggerService()
        let device = device ?? makeDevice()

        let store = BpmDeviceSettingsStore(
            device: device,
            notificationService: notification,
            scaleService: scaleService,
            bluetoothService: bluetooth,
            logger: logger,
            permissionsService: permissions
        )

        return (store, notification, scaleService, bluetooth, permissions)
    }

    private func makeDevice(
        id: String = "bpm-device-1",
        accountId: String = "acct-1",
        sku: String = "0603",
        broadcastIdString: String? = "ABCD-1234",
        isConnected: Bool = false
    ) -> Device {
        Device(
            id: id,
            accountId: accountId,
            sku: sku,
            deviceName: "Blood Pressure Monitor",
            deviceType: DeviceType.bpm.rawValue,
            broadcastIdString: broadcastIdString,
            isConnected: isConnected,
            bathScale: BathScale(scaleType: ScaleSourceType.bluetooth.rawValue, bodyComp: false)
        )
    }

    // MARK: - Initialization

    @Test("device property returns cached device after init")
    func deviceReturnsCachedDevice() {
        let device = makeDevice(id: "init-test")
        let (store, _, _, _, _) = makeSUT(device: device)

        #expect(store.device.id == "init-test")
    }

    @Test("isDeviceConnected is true when device connected and bluetooth enabled")
    func isDeviceConnectedWhenBluetoothOn() {
        let device = makeDevice(isConnected: true)
        let (store, _, _, _, _) = makeSUT(device: device)

        #expect(store.isDeviceConnected == true)
    }

    @Test("isDeviceConnected is false when bluetooth switch disabled")
    func isDeviceDisconnectedWhenBluetoothOff() {
        let device = makeDevice(isConnected: true)
        let permissions = MockPermissionsService()
        permissions.permissions = [
            .BLUETOOTH: .ENABLED,
            .BLUETOOTH_SWITCH: .DISABLED
        ]
        let (store, _, _, _, _) = makeSUT(device: device, permissions: permissions)

        #expect(store.isDeviceConnected == false)
    }

    // MARK: - Product Guide

    @Test("openProductGuide sets URL and shows browser")
    func openProductGuideSetsUrlAndShowsBrowser() {
        let (store, _, _, _, _) = makeSUT()

        store.openProductGuide(for: "0603")

        #expect(store.showProductBrowser == true)
        #expect(store.productURL == URL(string: "https://www.greatergoods.com/0603"))
    }

    // MARK: - Delete Device

    @Test("handleDeviceDelete shows confirmation alert")
    func handleDeviceDeleteShowsAlert() {
        let (store, notification, _, _, _) = makeSUT()

        store.handleDeviceDelete(deviceId: "bpm-device-1") { }

        #expect(notification.showAlertCalls == 1)
        #expect(notification.alertData != nil)
        #expect(notification.alertData?.buttons.count == 2)
    }

    @Test("delete confirmation calls deleteSingleDeviceEntry and syncs")
    func deleteConfirmationDeletesAndSyncs() async {
        let (store, notification, scaleService, bluetooth, _) = makeSUT()
        var successCalled = false

        store.handleDeviceDelete(deviceId: "bpm-device-1") {
            successCalled = true
        }

        // Tap the delete button (first button is danger/delete)
        let alert = notification.alertData
        alert?.buttons[0].action(nil)

        // Wait for async delete to complete
        let completed = await waitUntil {
            notification.dismissLoaderCalls >= 1
        }
        #expect(completed)

        #expect(scaleService.deleteSingleDeviceEntryCalls == 1)
        #expect(scaleService.syncAllScalesWithRemoteCalls >= 1)
        #expect(notification.showLoaderCalls == 1)
        #expect(notification.showToastCalls >= 1)
        #expect(successCalled == true)
    }

    @Test("delete failure shows error toast and does not call onSuccess")
    func deleteFailureShowsErrorToast() async {
        let scaleService = MockScaleService()
        scaleService.deleteSingleDeviceEntryError = NSError(domain: "test", code: 1)
        let (store, notification, _, _, _) = makeSUT(scaleService: scaleService)
        var successCalled = false

        store.handleDeviceDelete(deviceId: "bpm-device-1") {
            successCalled = true
        }

        let alert = notification.alertData
        alert?.buttons[0].action(nil)

        let completed = await waitUntil {
            notification.dismissLoaderCalls >= 1
        }
        #expect(completed)

        #expect(notification.showToastCalls >= 1)
        #expect(successCalled == false)
    }

    // MARK: - Utilities

    @MainActor
    private func waitUntil(
        timeoutNanoseconds: UInt64 = 3_000_000_000,
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
