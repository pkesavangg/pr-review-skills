import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct ScaleStoreTests {
    @Test("loads scales from publisher and sorts by latest createdAt first")
    func loadsAndSortsScaleStateFromPublisher() async {
        let (store, _, scaleService, _, _) = makeSUT()
        let oldest = makeScale(id: "oldest", createdAt: "2026-03-01T00:00:00Z")
        let newest = makeScale(id: "newest", createdAt: "2026-03-05T00:00:00Z")
        let missingDate = makeScale(id: "missing-date", createdAt: nil)

        scaleService.scales = [oldest.toSnapshot(), missingDate.toSnapshot(), newest.toSnapshot()]
        let loaded = await waitUntil {
            store.scales.map(\.id) == ["newest", "oldest", "missing-date"]
        }

        #expect(loaded == true)
    }

    @Test("sorting handles invalid createdAt values without crashing")
    func sortingHandlesInvalidCreatedAtGracefully() async {
        let (store, _, scaleService, _, _) = makeSUT()
        let valid = makeScale(id: "valid", createdAt: "2026-03-05T00:00:00Z")
        let invalid = makeScale(id: "invalid", createdAt: "not-a-date")
        let nilDate = makeScale(id: "nil-date", createdAt: nil)

        scaleService.scales = [invalid.toSnapshot(), nilDate.toSnapshot(), valid.toSnapshot()]
        let loaded = await waitUntil {
            store.scales.count == 3 &&
                store.scales.first?.id == "valid" &&
                store.scales.last?.id == "nil-date"
        }

        #expect(loaded == true)
    }

    @Test("update setup in progress toggles bluetooth state and clears discovery when ending")
    func updateSetupInProgressStatusUpdatesBluetoothState() async {
        let (store, _, _, bluetooth, _) = makeSUT()

        store.updateSetupInProgressStatus(true)
        #expect(bluetooth.isSetupInProgress == true)
        #expect(bluetooth.resumeSmartScanCalls == 0)
        #expect(bluetooth.syncDevicesCalls == 0)

        store.updateSetupInProgressStatus(false)
        let cleared = await waitUntil {
            bluetooth.resumeSmartScanCalls == 1 && bluetooth.syncDevicesCalls == 1
        }

        #expect(cleared == true)
        #expect(bluetooth.isSetupInProgress == false)
        #expect(bluetooth.lastResumeClearOnlyPairing == false)
        #expect(bluetooth.lastSyncedDevices.isEmpty)
    }

    @Test("determineConnectionStatus returns notConnected when bluetooth is off or scale is disconnected")
    func determineConnectionStatusHandlesDisconnectedStates() {
        let permissions = MockPermissionsService()
        permissions.setPermissions([.BLUETOOTH_SWITCH: .DISABLED])
        let (store, _, _, _, _) = makeSUT(permissions: permissions)
        let connectedScale = makeScale(id: "connected", isConnected: true)

        let bluetoothOff = store.determineConnectionStatus(for: connectedScale.toSnapshot())
        if case .notConnected = bluetoothOff {
            #expect(true)
        } else {
            Issue.record("Expected notConnected when Bluetooth switch is disabled")
        }

        permissions.setPermissions([.BLUETOOTH_SWITCH: .ENABLED])
        connectedScale.isConnected = false
        let disconnected = store.determineConnectionStatus(for: connectedScale.toSnapshot())
        if case .notConnected = disconnected {
            #expect(true)
        } else {
            Issue.record("Expected notConnected when scale is disconnected")
        }
    }

    @Test("determineConnectionStatus returns setupIncomplete and connected for btWifi scale settings states")
    func determineConnectionStatusForBtWifiModes() {
        let (store, _, _, _, _) = makeSUT()
        let setupIncomplete = makeScale(
            id: "setup-incomplete",
            isConnected: true,
            isWifiConfigured: false,
            sourceType: .btWifiR4,
            shouldMeasureImpedance: true
        )
        let weightOnlyConnected = makeScale(
            id: "weight-only",
            isConnected: true,
            isWifiConfigured: false,
            sourceType: .btWifiR4,
            shouldMeasureImpedance: false
        )

        let setupIncompleteStatus = store.determineConnectionStatus(for: setupIncomplete.toSnapshot())
        if case .setupIncomplete = setupIncompleteStatus {
            #expect(true)
        } else {
            Issue.record("Expected setupIncomplete for connected R4 scale with WiFi not configured")
        }

        let weightOnlyStatus = store.determineConnectionStatus(for: weightOnlyConnected.toSnapshot())
        if case .connected = weightOnlyStatus {
            #expect(true)
        } else {
            Issue.record("Expected connected for weight-only R4 scale with WiFi not configured")
        }
    }

    @Test("determineConnectionStatus returns noStatus for appsync scales")
    func determineConnectionStatusForAppSync() {
        let (store, _, _, _, _) = makeSUT()
        let appSyncScale = makeScale(id: "appsync", sku: "UNKNOWN-APPSYNC-SKU", sourceType: .appsync)

        let status = store.determineConnectionStatus(for: appSyncScale.toSnapshot())
        if case .noStatus = status {
            #expect(true)
        } else {
            Issue.record("Expected noStatus for appsync scale")
        }
    }

    @Test("handleDuplicateScale shows alert and executes pair action")
    func handleDuplicateScaleShowsAlertAndRunsPairAction() {
        let (store, notification, _, _, _) = makeSUT()
        var pairCalls = 0

        store.handleDuplicateScale(sku: "0375") {
            pairCalls += 1
        }

        #expect(notification.showAlertCalls == 1)
        notification.alertData?.buttons.last?.action(nil)
        #expect(pairCalls == 1)
    }

    @Test("openHelp presents model number help modal with backdrop dismiss enabled")
    func openHelpPresentsModal() {
        let (store, notification, _, _, _) = makeSUT()

        store.openHelp()

        #expect(notification.showModalCalls == 1)
        #expect(notification.modalViewData.count == 1)
        #expect(notification.modalViewData.first?.backdropDismiss == true)
    }

    @Test("resetForm clears and reinitializes add scale form state")
    func resetFormClearsAndReinitializesForm() {
        let (store, _, _, _, _) = makeSUT()
        store.addScaleForm.setModelNumber("0375")
        let oldForm = store.addScaleForm

        store.resetForm()

        #expect(store.addScaleForm.modelNumberValue.isEmpty)
        #expect(store.addScaleForm !== oldForm)
    }

    private func makeSUT(
        notification: MockNotificationHelperService? = nil,
        scaleService: MockScaleService? = nil,
        bluetooth: MockBluetoothService? = nil,
        logger: MockLoggerService? = nil,
        permissions: MockPermissionsService? = nil
    ) -> (ScaleStore, MockNotificationHelperService, MockScaleService, MockBluetoothService, MockPermissionsService) { // swiftlint:disable:this large_tuple
        let notification = notification ?? MockNotificationHelperService()
        let scaleService = scaleService ?? MockScaleService()
        let bluetooth = bluetooth ?? MockBluetoothService()
        let logger = logger ?? MockLoggerService()
        let permissions = permissions ?? MockPermissionsService()

        if permissions.permissions == nil {
            permissions.setPermissions([.BLUETOOTH_SWITCH: .ENABLED])
        }

        DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
        DependencyContainer.shared.register(scaleService as ScaleServiceProtocol)
        DependencyContainer.shared.register(bluetooth as BluetoothServiceProtocol)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)
        DependencyContainer.shared.register(permissions as PermissionsServiceProtocol)

        return (ScaleStore(), notification, scaleService, bluetooth, permissions)
    }

    private func makeScale(
        id: String,
        sku: String? = "R4-001",
        createdAt: String? = "2026-03-03T00:00:00Z",
        isConnected: Bool = true,
        isWifiConfigured: Bool = true,
        sourceType: ScaleSourceType = .btWifiR4,
        shouldMeasureImpedance: Bool = true
    ) -> Device {
        let scale = ScaleTestFixtures.makeDevice(id: id, accountId: "acct-1", displayName: id)
        scale.sku = sku
        scale.createdAt = createdAt
        scale.isConnected = isConnected
        scale.isWifiConfigured = isWifiConfigured
        scale.bathScale = BathScale(scaleType: sourceType.rawValue, bodyComp: true)
        if let preference = scale.r4ScalePreference {
            preference.shouldMeasureImpedance = shouldMeasureImpedance
        } else {
            scale.r4ScalePreference = R4ScalePreference(
                from: ScaleTestFixtures.makePreferenceDTO(scaleId: id, shouldMeasureImpedance: shouldMeasureImpedance),
                scaleId: id
            )
        }
        return scale
    }

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 1_000_000_000,
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
