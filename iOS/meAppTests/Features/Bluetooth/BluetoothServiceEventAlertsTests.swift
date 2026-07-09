// swiftlint:disable file_length
import Combine
import Foundation
import GGBluetoothSwiftPackage
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct BluetoothServiceEventAlertsTests {

    // MARK: - handleDeviceEventAlert: Guard Clauses & Early Returns

    @Test("handleDeviceEventAlert returns early for invalid device data (not GGDeviceDetails)")
    func handleDeviceEventAlertInvalidDeviceData() async {
        let logger = MockLoggerService()
        let notification = MockNotificationHelperService()
        let sut = makeSUT(logger: logger, notification: notification)

        await sut.handleDeviceEventAlert(MalformedScanData(), isDuplicateUserError: false)

        #expect(notification.showAlertCalls == 0)
        #expect(logger.messages.contains { $0.contains("Invalid device data for event alert") })
    }

    @Test("handleDeviceEventAlert returns early when isSetupInProgress is true")
    func handleDeviceEventAlertSetupInProgress() async {
        let logger = MockLoggerService()
        let notification = MockNotificationHelperService()
        let sut = makeSUT(logger: logger, notification: notification)
        sut.isSetupInProgress = true

        let deviceDetails = makeDeviceDetails(broadcastId: "SETUP-1")
        await sut.handleDeviceEventAlert(deviceDetails, isDuplicateUserError: false)

        #expect(notification.showAlertCalls == 0)
        #expect(logger.messages.contains { $0.contains("Invalid device data for event alert") })
    }

    @Test("handleDeviceEventAlert returns early when device is in skipDevices")
    func handleDeviceEventAlertSkippedDevice() async {
        let notification = MockNotificationHelperService()
        let sut = makeSUT(notification: notification)
        sut.skipDevices = ["SKIP-1"]

        let deviceDetails = makeDeviceDetails(broadcastId: "SKIP-1")
        await sut.handleDeviceEventAlert(deviceDetails, isDuplicateUserError: false)

        #expect(notification.showAlertCalls == 0)
    }

    @Test("handleDeviceEventAlert returns early when device is in reconnectAlertSkippedDevices")
    func handleDeviceEventAlertReconnectSkipped() async {
        let notification = MockNotificationHelperService()
        let sut = makeSUT(notification: notification)
        sut.reconnectAlertSkippedDevices = ["RECONNECT-SKIP-1"]

        let deviceDetails = makeDeviceDetails(broadcastId: "RECONNECT-SKIP-1")
        await sut.handleDeviceEventAlert(deviceDetails, isDuplicateUserError: false)

        #expect(notification.showAlertCalls == 0)
    }

    @Test("handleDeviceEventAlert returns early when device not found in bluetoothScales")
    func handleDeviceEventAlertDeviceNotInBluetoothScales() async {
        let logger = MockLoggerService()
        let notification = MockNotificationHelperService()
        let sut = makeSUT(logger: logger, notification: notification)
        sut.bluetoothScales = []

        let deviceDetails = makeDeviceDetails(broadcastId: "NOT-FOUND")
        await sut.handleDeviceEventAlert(deviceDetails, isDuplicateUserError: false)

        #expect(notification.showAlertCalls == 0)
        #expect(logger.messages.contains { $0.contains("Discovered scale not found in bluetoothScales") })
    }

    @Test("handleDeviceEventAlert returns early when getScaleUserList fails")
    func handleDeviceEventAlertUserListFails() async {
        let logger = MockLoggerService()
        let notification = MockNotificationHelperService()
        let sdk = MockBluetoothSDKClient()
        sdk.getUsersError = EventAlertTestError.sdkFailure
        let sut = makeSUT(logger: logger, sdk: sdk, notification: notification)
        let device = BluetoothTestFixtures.makeDevice(id: "dev-1", broadcastIdString: "FAIL-1")
        sut.bluetoothScales = [device.toSnapshot()]

        let deviceDetails = makeDeviceDetails(broadcastId: "FAIL-1")
        await sut.handleDeviceEventAlert(deviceDetails, isDuplicateUserError: false)

        #expect(notification.showAlertCalls == 0)
        #expect(logger.messages.contains { $0.contains("Failed to get scale user list for device event alert") })
    }

    // MARK: - handleDeviceEventAlert: Alert Display

    @Test("handleDeviceEventAlert shows reconnect alert when isDuplicateUserError is false")
    func handleDeviceEventAlertShowsReconnectAlert() async {
        let notification = MockNotificationHelperService()
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk, notification: notification)
        let device = BluetoothTestFixtures.makeDevice(id: "dev-r", broadcastIdString: "RECON-1")
        device.sku = "0375" // weight scale; isBpmDevice() must be false so getScaleUserList runs
        sut.bluetoothScales = [device.toSnapshot()]

        let deviceDetails = makeDeviceDetails(broadcastId: "RECON-1")
        await sut.handleDeviceEventAlert(deviceDetails, isDuplicateUserError: false)

        #expect(notification.showAlertCalls == 1)
        #expect(notification.alertData?.title == AlertStrings.ReconnectDeviceAlert.header)
        #expect(notification.alertData?.buttons.count == 2)
    }

    @Test("handleDeviceEventAlert shows duplicate user alert when isDuplicateUserError is true")
    func handleDeviceEventAlertShowsDuplicateAlert() async {
        let notification = MockNotificationHelperService()
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk, notification: notification)
        let device = BluetoothTestFixtures.makeDevice(id: "dev-d", broadcastIdString: "DUP-1")
        device.sku = "0375" // weight scale; isBpmDevice() must be false so getScaleUserList runs
        sut.bluetoothScales = [device.toSnapshot()]

        let deviceDetails = makeDeviceDetails(broadcastId: "DUP-1")
        await sut.handleDeviceEventAlert(deviceDetails, isDuplicateUserError: true)

        #expect(notification.showAlertCalls == 1)
        #expect(notification.alertData?.title == AlertStrings.DuplicateUserAlert.header)
        #expect(notification.alertData?.buttons.count == 2)
    }

    @Test("handleDeviceEventAlert sets canShowScaleDiscoveredModal to false")
    func handleDeviceEventAlertSetsModalFlag() async {
        let sdk = MockBluetoothSDKClient()
        let notification = MockNotificationHelperService()
        let sut = makeSUT(sdk: sdk, notification: notification)
        sut.setCanShowScaleDiscoveredModal(true)
        let device = BluetoothTestFixtures.makeDevice(id: "dev-m", broadcastIdString: "MODAL-1")
        device.sku = "0375" // weight scale; isBpmDevice() must be false so getScaleUserList runs
        sut.bluetoothScales = [device.toSnapshot()]

        let deviceDetails = makeDeviceDetails(broadcastId: "MODAL-1")
        await sut.handleDeviceEventAlert(deviceDetails, isDuplicateUserError: false)

        #expect(sut.canShowScaleDiscoveredModal == false)
    }

    @Test("reconnect alert cancel button adds device to reconnectAlertSkippedDevices")
    func reconnectAlertCancelButtonSkipsDevice() async {
        let notification = MockNotificationHelperService()
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk, notification: notification)
        let device = BluetoothTestFixtures.makeDevice(id: "dev-cancel", broadcastIdString: "CANCEL-1")
        device.sku = "0375" // weight scale; isBpmDevice() must be false so getScaleUserList runs
        sut.bluetoothScales = [device.toSnapshot()]

        let deviceDetails = makeDeviceDetails(broadcastId: "CANCEL-1")
        await sut.handleDeviceEventAlert(deviceDetails, isDuplicateUserError: false)

        // Trigger the cancel (secondary) button action
        let cancelButton = notification.alertData?.buttons.first { $0.type == .secondary }
        #expect(cancelButton != nil)
        cancelButton?.action(nil)

        // Wait for the Task inside the cancel action to complete
        let skipped = await waitUntil { sut.reconnectAlertSkippedDevices.contains("CANCEL-1") }
        #expect(skipped == true)
    }

    @Test("duplicate alert cancel button adds device to reconnectAlertSkippedDevices")
    func duplicateAlertCancelButtonSkipsDevice() async {
        let notification = MockNotificationHelperService()
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk, notification: notification)
        let device = BluetoothTestFixtures.makeDevice(id: "dev-dup-cancel", broadcastIdString: "DUP-CANCEL-1")
        device.sku = "0375" // weight scale; isBpmDevice() must be false so getScaleUserList runs
        sut.bluetoothScales = [device.toSnapshot()]

        let deviceDetails = makeDeviceDetails(broadcastId: "DUP-CANCEL-1")
        await sut.handleDeviceEventAlert(deviceDetails, isDuplicateUserError: true)

        let cancelButton = notification.alertData?.buttons.first { $0.type == .secondary }
        #expect(cancelButton != nil)
        cancelButton?.action(nil)

        let skipped = await waitUntil { sut.reconnectAlertSkippedDevices.contains("DUP-CANCEL-1") }
        #expect(skipped == true)
    }

    @Test("reconnect button triggers onOpenDeviceSetup callback")
    func reconnectButtonTriggersOpenScaleSetup() async {
        let notification = MockNotificationHelperService()
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk, notification: notification)
        let device = BluetoothTestFixtures.makeDevice(id: "dev-setup", broadcastIdString: "SETUP-1")
        device.sku = "0375" // weight scale; isBpmDevice() must be false so getScaleUserList runs
        sut.bluetoothScales = [device.toSnapshot()]

        var callbackInvoked = false
        var callbackIsDuplicate = false
        sut.onOpenDeviceSetup = { _, _, _, isDuplicate in
            callbackInvoked = true
            callbackIsDuplicate = isDuplicate
        }

        let deviceDetails = makeDeviceDetails(broadcastId: "SETUP-1")
        await sut.handleDeviceEventAlert(deviceDetails, isDuplicateUserError: false)

        let reconnectButton = notification.alertData?.buttons.first { $0.type == .primary }
        #expect(reconnectButton != nil)
        reconnectButton?.action(nil)

        let invoked = await waitUntil { callbackInvoked }
        #expect(invoked == true)
        #expect(callbackIsDuplicate == false)
    }

    @Test("duplicate reconnect button triggers onOpenDeviceSetup with isDuplicate true")
    func duplicateReconnectButtonTriggersOpenScaleSetupWithDuplicate() async {
        let notification = MockNotificationHelperService()
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk, notification: notification)
        let device = BluetoothTestFixtures.makeDevice(id: "dev-dup-setup", broadcastIdString: "DUP-SETUP-1")
        device.sku = "0375" // weight scale; isBpmDevice() must be false so getScaleUserList runs
        sut.bluetoothScales = [device.toSnapshot()]

        var callbackIsDuplicate = false
        var callbackInvoked = false
        sut.onOpenDeviceSetup = { _, _, _, isDuplicate in
            callbackInvoked = true
            callbackIsDuplicate = isDuplicate
        }

        let deviceDetails = makeDeviceDetails(broadcastId: "DUP-SETUP-1")
        await sut.handleDeviceEventAlert(deviceDetails, isDuplicateUserError: true)

        let reconnectButton = notification.alertData?.buttons.first { $0.type == .primary }
        reconnectButton?.action(nil)

        let invoked = await waitUntil { callbackInvoked }
        #expect(invoked == true)
        #expect(callbackIsDuplicate == true)
    }

    // MARK: - handleDeviceEventAlert: openScaleSetup deletes user when userToDelete has token

    // MARK: - findUserToDelete

    @Test("findUserToDelete returns nil for empty user list")
    func findUserToDeleteEmptyList() {
        let sut = makeSUT()
        let device = BluetoothTestFixtures.makeDevice(id: "dev-1", broadcastIdString: "ABC")
        sut.bluetoothScales = [device.toSnapshot()]

        let result = sut.findUserToDelete(userList: [], discoveredScale: device.toSnapshot())

        #expect(result == nil)
    }

    @Test("findUserToDelete returns nil when no matching scale in bluetoothScales")
    func findUserToDeleteNoMatchingScale() {
        let sut = makeSUT()
        sut.bluetoothScales = []
        let device = BluetoothTestFixtures.makeDevice(id: "dev-1", broadcastIdString: "ABC")
        let user = DeviceUser(name: "Test", token: "tok", lastActive: 1, isBodyMetricsEnabled: false)

        let result = sut.findUserToDelete(userList: [user], discoveredScale: device.toSnapshot())

        #expect(result == nil)
    }

    @Test("findUserToDelete returns nil for non-R4 scale type")
    func findUserToDeleteNonR4Scale() {
        let scale = MockScaleService()
        let sut = makeSUT(scale: scale)
        let device = BluetoothTestFixtures.makeDevice(
            id: "dev-1",
            broadcastIdString: "ABC",
            bathScale: BathScale(scaleType: DeviceSourceType.wifi.rawValue, bodyComp: false)
        )
        device.r4ScalePreference = R4ScalePreference(
            scaleId: "dev-1",
            displayName: "Test",
            displayMetrics: [],
            shouldFactoryReset: false,
            shouldMeasureImpedance: false,
            shouldMeasurePulse: false,
            timeFormat: "12",
            tzOffset: 0,
            wifiFotaScheduleTime: 0,
            updatedAt: nil
        )
        sut.bluetoothScales = [device.toSnapshot()]
        let user = DeviceUser(name: "Test", token: "tok", lastActive: 1, isBodyMetricsEnabled: false)

        let result = sut.findUserToDelete(userList: [user], discoveredScale: device.toSnapshot())

        #expect(result == nil)
    }

    @Test("findUserToDelete returns nil when broadcastIds do not match")
    func findUserToDeleteBroadcastMismatch() {
        let scale = MockScaleService()
        let sut = makeSUT(scale: scale)
        let scaleDevice = BluetoothTestFixtures.makeDevice(
            id: "dev-1",
            broadcastIdString: "DIFFERENT",
            bathScale: BathScale(scaleType: DeviceSourceType.btWifiR4.rawValue, bodyComp: true)
        )
        scaleDevice.broadcastId = 100
        scaleDevice.r4ScalePreference = R4ScalePreference(
            scaleId: "dev-1",
            displayName: "Test",
            displayMetrics: [],
            shouldFactoryReset: false,
            shouldMeasureImpedance: false,
            shouldMeasurePulse: false,
            timeFormat: "12",
            tzOffset: 0,
            wifiFotaScheduleTime: 0,
            updatedAt: nil
        )
        sut.bluetoothScales = [scaleDevice.toSnapshot()]

        let discoveredDevice = BluetoothTestFixtures.makeDevice(id: "dev-2", broadcastIdString: "ABC")
        discoveredDevice.broadcastId = 200
        let user = DeviceUser(name: "Test", token: "tok", lastActive: 1, isBodyMetricsEnabled: false)

        let result = sut.findUserToDelete(userList: [user], discoveredScale: discoveredDevice.toSnapshot())

        #expect(result == nil)
    }

    @Test("findUserToDelete returns nil when names do not match")
    func findUserToDeleteNameMismatch() {
        let scale = MockScaleService()
        let sut = makeSUT(scale: scale)
        let device = BluetoothTestFixtures.makeDevice(
            id: "dev-1",
            broadcastIdString: "ABC",
            bathScale: BathScale(scaleType: DeviceSourceType.btWifiR4.rawValue, bodyComp: true)
        )
        device.r4ScalePreference = R4ScalePreference(
            scaleId: "dev-1",
            displayName: "Other Name",
            displayMetrics: [],
            shouldFactoryReset: false,
            shouldMeasureImpedance: false,
            shouldMeasurePulse: false,
            timeFormat: "12",
            tzOffset: 0,
            wifiFotaScheduleTime: 0,
            updatedAt: nil
        )
        sut.bluetoothScales = [device.toSnapshot()]
        let user = DeviceUser(name: "Test", token: "tok", lastActive: 1, isBodyMetricsEnabled: false)

        let result = sut.findUserToDelete(userList: [user], discoveredScale: device.toSnapshot())

        #expect(result == nil)
    }

    @Test("findUserToDelete returns matching user when all conditions are met")
    func findUserToDeleteMatch() {
        let scale = MockScaleService()
        let sut = makeSUT(scale: scale)
        let device = BluetoothTestFixtures.makeDevice(
            id: "dev-1",
            broadcastIdString: "ABC",
            bathScale: BathScale(scaleType: DeviceSourceType.btWifiR4.rawValue, bodyComp: true)
        )
        let pref = R4ScalePreference(
            scaleId: "dev-1",
            displayName: "Test User",
            displayMetrics: [],
            shouldFactoryReset: false,
            shouldMeasureImpedance: false,
            shouldMeasurePulse: false,
            timeFormat: "12",
            tzOffset: 0,
            wifiFotaScheduleTime: 0,
            updatedAt: nil
        )
        device.r4ScalePreference = pref
        scale.attachedPreferences["dev-1"] = pref
        sut.bluetoothScales = [device.toSnapshot()]
        let user = DeviceUser(name: "test user", token: "tok-match", lastActive: 5, isBodyMetricsEnabled: true)

        let result = sut.findUserToDelete(userList: [user], discoveredScale: device.toSnapshot())

        #expect(result != nil)
        #expect(result?.token == "tok-match")
    }

    @Test("findUserToDelete uses fetchAttachedPreference when available")
    func findUserToDeleteUsesFetchedPreference() {
        let scale = MockScaleService()
        let sut = makeSUT(scale: scale)
        let device = BluetoothTestFixtures.makeDevice(
            id: "dev-1",
            broadcastIdString: "ABC",
            bathScale: BathScale(scaleType: DeviceSourceType.btWifiR4.rawValue, bodyComp: true)
        )
        // Set r4ScalePreference with one name
        let originalPref = R4ScalePreference(
            scaleId: "dev-1",
            displayName: "Original Name",
            displayMetrics: [],
            shouldFactoryReset: false,
            shouldMeasureImpedance: false,
            shouldMeasurePulse: false,
            timeFormat: "12",
            tzOffset: 0,
            wifiFotaScheduleTime: 0,
            updatedAt: nil
        )
        device.r4ScalePreference = originalPref
        // But attachedPreferences returns a different name (simulating fetched/updated value)
        let fetchedPref = R4ScalePreference(
            scaleId: "dev-1",
            displayName: "Fetched Name",
            displayMetrics: [],
            shouldFactoryReset: false,
            shouldMeasureImpedance: false,
            shouldMeasurePulse: false,
            timeFormat: "12",
            tzOffset: 0,
            wifiFotaScheduleTime: 0,
            updatedAt: nil
        )
        scale.attachedPreferences[originalPref.id] = fetchedPref
        sut.bluetoothScales = [device.toSnapshot()]
        let user = DeviceUser(name: "fetched name", token: "tok-fetched", lastActive: 1, isBodyMetricsEnabled: false)

        let result = sut.findUserToDelete(userList: [user], discoveredScale: device.toSnapshot())

        #expect(result != nil)
        #expect(result?.token == "tok-fetched")
    }

    // MARK: - deleteUserByToken & deleteScaleByBroadcastId

    @Test("deleteUserByToken success returns success result")
    func deleteUserByTokenSuccess() async {
        let sdk = MockBluetoothSDKClient()
        sdk.deleteUserResult = .SUCCESS
        let sut = makeSUT(sdk: sdk)
        sut.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", email: "a@b.com", isLoggedIn: true, isActiveAccount: true)

        let result = await sut.deleteUserByToken(broadcastId: "DEL-BID", token: "tok-1", disconnect: false)

        guard case .success(let response) = result else {
            Issue.record("Expected success")
            return
        }
        #expect(response == .success)
        #expect(sdk.deletedDevices.count == 1)
    }

    @Test("deleteScaleByBroadcastId creates temp device with correct broadcastIdString and token")
    func deleteScaleByBroadcastIdCreatesCorrectDevice() async {
        let sdk = MockBluetoothSDKClient()
        sdk.deleteUserResult = .SUCCESS
        let sut = makeSUT(sdk: sdk)
        sut.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-2", email: "b@c.com", isLoggedIn: true, isActiveAccount: true)

        let result = await sut.deleteScaleByBroadcastId(broadcastId: "MY-SCALE", token: "my-token", disconnect: true)

        guard case .success = result else {
            Issue.record("Expected success")
            return
        }
        #expect(sdk.deletedDevices.count == 1)
        #expect(sdk.deletedDevices.first?.device.broadcastId == "MY-SCALE")
        #expect(sdk.deletedDevices.first?.device.token == "my-token")
    }

    @Test("deleteScaleByBroadcastId returns failure when SDK delete fails")
    func deleteScaleByBroadcastIdFailure() async {
        let sdk = MockBluetoothSDKClient()
        sdk.deleteUserError = EventAlertTestError.sdkFailure
        let sut = makeSUT(sdk: sdk)
        sut.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-3", email: "c@d.com", isLoggedIn: true, isActiveAccount: true)

        let result = await sut.deleteScaleByBroadcastId(broadcastId: "FAIL-SCALE", token: "tok", disconnect: false)

        guard case .failure = result else {
            Issue.record("Expected failure")
            return
        }
    }

    // MARK: - Deduplication / Repeated Event Handling

    @Test("repeated events for the same device in skipDevices do not produce alerts")
    func repeatedEventsWithSkipDevicesNoAlerts() async {
        let notification = MockNotificationHelperService()
        let sut = makeSUT(notification: notification)
        sut.skipDevices = ["REPEAT-1"]

        let deviceDetails = makeDeviceDetails(broadcastId: "REPEAT-1")
        await sut.handleDeviceEventAlert(deviceDetails, isDuplicateUserError: false)
        await sut.handleDeviceEventAlert(deviceDetails, isDuplicateUserError: true)
        await sut.handleDeviceEventAlert(deviceDetails, isDuplicateUserError: false)

        #expect(notification.showAlertCalls == 0)
    }

    @Test("after cancel, repeated event for same device is suppressed via reconnectAlertSkippedDevices")
    func afterCancelRepeatedEventSuppressed() async {
        let notification = MockNotificationHelperService()
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk, notification: notification)
        let device = BluetoothTestFixtures.makeDevice(id: "dev-repeat", broadcastIdString: "RPT-1")
        device.sku = "0375" // weight scale; isBpmDevice() must be false so getScaleUserList runs
        sut.bluetoothScales = [device.toSnapshot()]

        let deviceDetails = makeDeviceDetails(broadcastId: "RPT-1")

        // First call shows alert
        await sut.handleDeviceEventAlert(deviceDetails, isDuplicateUserError: false)
        #expect(notification.showAlertCalls == 1)

        // Trigger cancel button to add to skipped list
        let cancelButton = notification.alertData?.buttons.first { $0.type == .secondary }
        cancelButton?.action(nil)
        let skipped = await waitUntil { sut.reconnectAlertSkippedDevices.contains("RPT-1") }
        #expect(skipped == true)

        // Second call should be suppressed
        await sut.handleDeviceEventAlert(deviceDetails, isDuplicateUserError: false)
        #expect(notification.showAlertCalls == 1)
    }

    // MARK: - Helpers

    private func makeSUT(
        account: MockAccountService? = nil,
        scale: MockScaleService? = nil,
        entry: MockEntryService? = nil,
        logger: MockLoggerService? = nil,
        discovery: MockBLEDiscoveryManager? = nil,
        sdk: MockBluetoothSDKClient? = nil,
        notification: MockNotificationHelperService? = nil
    ) -> BluetoothService {
        BluetoothService(
            accountService: account ?? MockAccountService(),
            deviceService: scale ?? MockScaleService(),
            entryService: entry ?? MockEntryService(),
            babyService: MockBabyService(),
            logger: logger ?? MockLoggerService(),
            discoveryManager: discovery ?? MockBLEDiscoveryManager(),
            ggBleSDK: sdk ?? MockBluetoothSDKClient(),
            notificationService: notification ?? MockNotificationHelperService()
        )
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
}

// MARK: - Test Helpers (file-private)

private enum EventAlertTestError: Error {
    case sdkFailure
}

private struct MalformedScanData: GGScanResponseData {}

private func makeDeviceDetails(
    broadcastId: String,
    protocolType: String = "A6"
) -> GGDeviceDetails {
    decodeJSON(
        [
            "manufacturerName": "Weight Gurus",
            "modelNumber": "Model-\(protocolType)",
            "serialNumber": "Serial-\(broadcastId)",
            "firmwareRevision": "FW-1",
            "hardwareRevision": "HW-1",
            "softwareRevision": "SW-1",
            "systemID": "SYS-\(broadcastId)",
            "deviceName": protocolType == "R4" ? "Smart Scale" : "Scale",
            "broadcastId": broadcastId,
            "broadcastIdString": broadcastId,
            "password": "00000000",
            "macAddress": "AA:BB:CC:DD:EE:FF",
            "wifiMacAddress": "11:22:33:44:55:66",
            "identifier": "identifier-\(broadcastId)",
            "protocolType": protocolType,
            "isWifiConfigured": true,
            "sessionImpedanceSwitchState": true,
            "impedanceSwitchState": true,
            "startAnimationState": true,
            "endAnimationState": false,
            "batteryLevel": 90,
            "userNumber": 2,
            "heartRateState": true
        ],
        as: GGDeviceDetails.self
    )
}

private func decodeJSON<T: Decodable>(_ object: [String: Any], as type: T.Type) -> T {
    let data = try! JSONSerialization.data(withJSONObject: object) // swiftlint:disable:this force_try
    return try! JSONDecoder().decode(type, from: data) // swiftlint:disable:this force_try
}

private func decodeScaleUsers(_ users: [[String: Any]]) -> GGScaleUserResponse {
    decodeJSON(["user": users], as: GGScaleUserResponse.self)
}
