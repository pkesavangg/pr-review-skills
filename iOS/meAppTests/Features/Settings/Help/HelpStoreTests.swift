//
//  HelpStoreTests.swift
//  meAppTests
//

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct HelpStoreTests {

    // MARK: - Computed Properties

    @Test("isSendScaleLogEnabled returns true when more than one scale is present")
    func isSendScaleLogEnabled_multipleScales_returnsTrue() {
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scales = [
            ScaleTestFixtures.makeDevice(id: "scale-1").toSnapshot(),
            ScaleTestFixtures.makeDevice(id: "scale-2").toSnapshot()
        ]
        #expect(store.isSendScaleLogEnabled == true)
    }

    @Test("isSendScaleLogEnabled returns true when single connected scale is present")
    func isSendScaleLogEnabled_singleConnectedScale_returnsTrue() {
        let (store, _, _, _, _, _, _) = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "scale-1")
        store.scales = [device.toSnapshot(isConnected: true)]
        #expect(store.isSendScaleLogEnabled == true)
    }

    @Test("isSendScaleLogEnabled returns false when single disconnected scale is present")
    func isSendScaleLogEnabled_singleDisconnectedScale_returnsFalse() {
        let (store, _, _, _, _, _, _) = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "scale-1")
        device.isConnected = false
        store.scales = [device.toSnapshot()]
        #expect(store.isSendScaleLogEnabled == false)
    }

    @Test("shouldShowScaleTroubleshooting returns false when no scales are present")
    func shouldShowScaleTroubleshooting_emptyScales_returnsFalse() {
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scales = []
        #expect(store.shouldShowScaleTroubleshooting == false)
    }

    @Test("shouldShowScaleTroubleshooting returns true when scales are present")
    func shouldShowScaleTroubleshooting_hasScales_returnsTrue() {
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scales = [ScaleTestFixtures.makeDevice(id: "scale-1").toSnapshot()]
        #expect(store.shouldShowScaleTroubleshooting == true)
    }

    // MARK: - Init / Publisher

    @Test("init filters scalesPublisher to only include btWifiR4 scales")
    func init_scalesPublisher_filtersOnlyBtWifiR4Scales() async {
        let scaleService = MockScaleService()
        let (store, _, _, _, _, _, _) = makeSUT(scaleService: scaleService)

        let r4Device = ScaleTestFixtures.makeDevice(id: "r4-scale")
        let nonR4Device = Device(id: "non-r4", accountId: "acct-1", hasServerID: false)
        nonR4Device.bathScale = BathScale(scaleType: DeviceSourceType.bluetooth.rawValue, bodyComp: false)
        let noScaleDevice = Device(id: "no-scale", accountId: "acct-1", hasServerID: false)

        scaleService.scales = [r4Device.toSnapshot(), nonR4Device.toSnapshot(), noScaleDevice.toSnapshot()]

        let filtered = await waitUntil { store.scales.count == 1 }
        #expect(filtered == true)
        #expect(store.scales.first?.id == "r4-scale")
    }

    // MARK: - openProductManual

    @Test("openProductManual sets productURL and shows product browser")
    func openProductManual_setsURLAndShowsBrowser() {
        let (store, _, _, _, _, _, _) = makeSUT()
        store.openProductManual(sku: "0412")
        #expect(store.showProductBrowser == true)
        #expect(store.productURL?.absoluteString == "\(AppConstants.Product.baseURL)/0412")
    }

    // MARK: - handleHeaderTap

    @Test("handleHeaderTap triggers debug menu after exactly five taps within the time window")
    func handleHeaderTap_fiveTapsWithinWindow_triggersDebugMenu() {
        let (store, _, _, _, _, _, _) = makeSUT()
        for _ in 1...5 { store.handleHeaderTap() }
        #expect(store.showDebugMenu == true)
    }

    @Test("handleHeaderTap does not trigger debug menu after only four taps")
    func handleHeaderTap_fourTaps_doesNotTriggerDebugMenu() {
        let (store, _, _, _, _, _, _) = makeSUT()
        for _ in 1...4 { store.handleHeaderTap() }
        #expect(store.showDebugMenu == false)
    }

    @Test("handleHeaderTap resets counter when taps fall outside the 5-second window")
    func handleHeaderTap_tapsOutsideWindow_resetsCounterAndDoesNotTrigger() {
        let (store, _, _, _, _, _, _) = makeSUT()
        for _ in 1...4 { store.handleHeaderTap() }
        // debug menu must not yet be shown after four rapid taps
        #expect(store.showDebugMenu == false)
    }

    // MARK: - dismissDebugMenu

    @Test("dismissDebugMenu sets showDebugMenu to false")
    func dismissDebugMenu_setsShowDebugMenuFalse() {
        let (store, _, _, _, _, _, _) = makeSUT()
        for _ in 1...5 { store.handleHeaderTap() }
        #expect(store.showDebugMenu == true)
        store.dismissDebugMenu()
        #expect(store.showDebugMenu == false)
    }

    // MARK: - sendWeightGurusLog

    @Test("sendWeightGurusLog success shows loader, dismisses loader, and shows success toast")
    func sendWeightGurusLog_success_showsLoaderAndSuccessToast() async {
        let (store, notification, _, logger, _, _, _) = makeSUT()
        store.sendWeightGurusLog()
        let done = await waitUntil { notification.dismissLoaderCalls >= 1 }
        #expect(done == true)
        #expect(notification.showLoaderCalls == 1)
        #expect(notification.dismissLoaderCalls >= 1)
        #expect(logger.sendLogsToServerCalls >= 1)
        #expect(notification.showToastCalls == 1)
        #expect(notification.toastData?.message == ToastStrings.logsSent)
    }

    @Test("sendWeightGurusLog with noInternet error does not show error toast")
    func sendWeightGurusLog_noInternetError_noToastShown() async {
        let logger = MockHelpStoreLoggerService()
        logger.sendLogsToServerError = HTTPError.noInternet
        let (store, notification, _, _, _, _, _) = makeSUT(loggerService: logger)
        store.sendWeightGurusLog()
        let done = await waitUntil { notification.dismissLoaderCalls >= 1 }
        #expect(done == true)
        #expect(logger.sendLogsToServerCalls >= 1)
        #expect(notification.showToastCalls == 0)
        #expect(notification.dismissLoaderCalls >= 1)
    }

    @Test("sendWeightGurusLog with generic error shows error toast")
    func sendWeightGurusLog_otherError_showsErrorToast() async {
        let logger = MockHelpStoreLoggerService()
        logger.sendLogsToServerError = HelpStoreTestError.genericFailure
        let (store, notification, _, _, _, _, _) = makeSUT(loggerService: logger)
        store.sendWeightGurusLog()
        let done = await waitUntil { notification.dismissLoaderCalls >= 1 }
        #expect(done == true)
        #expect(logger.sendLogsToServerCalls >= 1)
        #expect(notification.showToastCalls == 1)
        #expect(notification.toastData?.title == ToastStrings.somethingWentWrongTitle)
        #expect(notification.dismissLoaderCalls >= 1)
    }

    // MARK: - resyncEntries

    @Test("resyncEntries when online clears data, resyncs, and shows success toast")
    func resyncEntries_onlineSuccess_clearsDataAndShowsSuccessToast() async {
        guard NetworkMonitor.shared.isConnected else { return }
        let entry = MockHelpStoreEntryService()
        let (store, notification, _, _, _, _, _) = makeSUT(entryService: entry)
        store.resyncEntries()
        let done = await waitUntil { entry.syncAllEntriesWithRemoteCalls == 1 }
        #expect(done == true)
        #expect(entry.clearAllDataCalls == 1)
        #expect(entry.clearLastSyncTimestampCalls == 1)
        #expect(notification.showToastCalls == 1)
        #expect(notification.toastData?.message == ToastStrings.synced)
    }

    @Test("resyncEntries when online and clearLastSyncTimestamp fails shows error toast")
    func resyncEntries_onlineFailure_showsErrorToast() async {
        guard NetworkMonitor.shared.isConnected else { return }
        let entry = MockHelpStoreEntryService()
        entry.clearLastSyncTimestampError = HelpStoreTestError.genericFailure
        let (store, notification, _, _, _, _, _) = makeSUT(entryService: entry)
        store.resyncEntries()
        let done = await waitUntil { entry.clearLastSyncTimestampCalls == 1 }
        #expect(done == true)
        #expect(notification.showToastCalls == 1)
        #expect(notification.toastData?.title == ToastStrings.somethingWentWrongTitle)
        #expect(notification.dismissLoaderCalls == 1)
    }

    // MARK: - clearAllLocalData

    @Test("clearAllLocalData success shows success alert after completing all steps")
    func clearAllLocalData_success_showsSuccessAlert() async {
        let (store, notification, _, _, _, _, _) = makeSUT()
        store.clearAllLocalData()
        let done = await waitUntil(timeoutNanoseconds: 5_000_000_000) {
            notification.showAlertCalls == 1
        }
        #expect(done == true)
        #expect(notification.alertData?.title == AlertStrings.DataClearingAlert.successHeader)
    }

    // MARK: - showAppRateModal

    @Test("showAppRateModal logs an info message and requests review via handler (no real modal)")
    func showAppRateModal_logsAndCallsHandler() async {
        let (store, _, _, logger, _, _, appReview) = makeSUT()
        store.showAppRateModal()
        let called = await waitUntil { appReview.triggerAppReviewCalls == 1 }
        #expect(called == true)
        #expect(logger.messages.contains { $0.contains("Presenting app rating modal") })
        #expect(appReview.lastIsFromDebug == true)
    }

    // MARK: - sendScaleLogHandler

    @Test("sendScaleLogHandler with explicit device sends logs for that device without showing sheet")
    func sendScaleLogHandler_withDeviceArg_sendsForThatDevice() async {
        let bluetooth = MockHelpStoreBluetoothService()
        let (store, notification, _, _, _, _, _) = makeSUT(bluetoothService: bluetooth)
        let device = ScaleTestFixtures.makeDevice(id: "target-scale")
        store.sendScaleLogHandler(device: device.toSnapshot())
        let done = await waitUntil { bluetooth.getDeviceLogsCalls == 1 }
        #expect(done == true)
        #expect(bluetooth.lastGetDeviceLogsBroadcastId == device.broadcastIdString)
        #expect(store.showScaleLogSheet == false)
        #expect(notification.showLoaderCalls == 1)
    }

    @Test("sendScaleLogHandler with no argument and a single scale sends directly")
    func sendScaleLogHandler_noArgSingleScale_sendsDirectly() async {
        let bluetooth = MockHelpStoreBluetoothService()
        let (store, _, _, _, _, _, _) = makeSUT(bluetoothService: bluetooth)
        store.scales = [ScaleTestFixtures.makeDevice(id: "single-scale").toSnapshot()]
        store.sendScaleLogHandler()
        let done = await waitUntil { bluetooth.getDeviceLogsCalls == 1 }
        #expect(done == true)
        #expect(store.showScaleLogSheet == false)
    }

    @Test("sendScaleLogHandler with no argument and multiple scales shows the scale log sheet")
    func sendScaleLogHandler_noArgMultipleScales_showsSheet() {
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scales = [
            ScaleTestFixtures.makeDevice(id: "scale-a").toSnapshot(),
            ScaleTestFixtures.makeDevice(id: "scale-b").toSnapshot()
        ]
        store.sendScaleLogHandler()
        #expect(store.showScaleLogSheet == true)
    }

    // MARK: - sendScaleLogsToServer (via sendScaleLogHandler with single scale)

    @Test("sendScaleLogHandler single-scale success shows success toast and hides scale log sheet")
    func sendScaleLogsToServer_success_showsToastAndHidesSheet() async {
        let bluetooth = MockHelpStoreBluetoothService()
        bluetooth.getDeviceLogsResult = .success(DeviceLogs(logs: [DeviceLogEntry(macAddress: "AA:BB", log: "test")]))
        let (store, notification, _, logger, _, _, _) = makeSUT(bluetoothService: bluetooth)
        store.scales = [ScaleTestFixtures.makeDevice(id: "ok-scale").toSnapshot()]
        store.showScaleLogSheet = true
        store.sendScaleLogHandler()
        let done = await waitUntil { logger.sendScaleLogsToServerCalls == 1 }
        #expect(done == true)
        #expect(notification.showToastCalls == 1)
        #expect(notification.toastData?.message == ToastStrings.logsSent)
        let sheetHidden = await waitUntil { store.showScaleLogSheet == false }
        #expect(sheetHidden == true)
    }

    @Test("sendScaleLogHandler single-scale Bluetooth failure shows error toast")
    func sendScaleLogsToServer_bluetoothGetLogsFails_showsErrorToast() async {
        let bluetooth = MockHelpStoreBluetoothService()
        bluetooth.getDeviceLogsResult = .failure(.notImplemented)
        let (store, notification, _, _, _, _, _) = makeSUT(bluetoothService: bluetooth)
        store.scales = [ScaleTestFixtures.makeDevice(id: "fail-scale").toSnapshot()]
        store.sendScaleLogHandler()
        let done = await waitUntil { bluetooth.getDeviceLogsCalls == 1 }
        #expect(done == true)
        let toastShown = await waitUntil { notification.showToastCalls == 1 }
        #expect(toastShown == true)
        #expect(notification.toastData?.title == ToastStrings.somethingWentWrongTitle)
    }

    @Test("sendScaleLogHandler single-scale upload failure shows error toast")
    func sendScaleLogsToServer_uploadFails_showsErrorToast() async {
        let bluetooth = MockHelpStoreBluetoothService()
        let logger = MockHelpStoreLoggerService()
        logger.sendScaleLogsToServerError = HelpStoreTestError.genericFailure
        let (store, notification, _, _, _, _, _) = makeSUT(loggerService: logger, bluetoothService: bluetooth)
        store.scales = [ScaleTestFixtures.makeDevice(id: "upload-fail-scale").toSnapshot()]
        store.sendScaleLogHandler()
        let done = await waitUntil { logger.sendScaleLogsToServerCalls == 1 }
        #expect(done == true)
        let toastShown = await waitUntil { notification.showToastCalls == 1 }
        #expect(toastShown == true)
        #expect(notification.toastData?.title == ToastStrings.somethingWentWrongTitle)
    }

    @Test("sendScaleLogHandler single-scale noInternet upload error does not show toast")
    func sendScaleLogsToServer_noInternetUploadError_noToast() async {
        let bluetooth = MockHelpStoreBluetoothService()
        let logger = MockHelpStoreLoggerService()
        logger.sendScaleLogsToServerError = HTTPError.noInternet
        let (store, notification, _, _, _, _, _) = makeSUT(loggerService: logger, bluetoothService: bluetooth)
        store.scales = [ScaleTestFixtures.makeDevice(id: "no-internet-scale").toSnapshot()]
        store.sendScaleLogHandler()
        let done = await waitUntil { logger.sendScaleLogsToServerCalls == 1 }
        #expect(done == true)
        #expect(notification.showToastCalls == 0)
        #expect(notification.dismissLoaderCalls == 1)
    }

    // MARK: - openHelp

    @Test("openHelp presents a modal")
    func openHelp_showsModal() {
        let (store, notification, _, _, _, _, _) = makeSUT()
        store.openHelp()
        #expect(notification.showModalCalls == 1)
    }
}

// MARK: - Test Errors

private enum HelpStoreTestError: Error {
    case genericFailure
}

// MARK: - makeSUT

@MainActor
private func makeSUT(
    accountService: MockAccountService? = nil,
    notificationService: MockNotificationHelperService? = nil,
    entryService: MockHelpStoreEntryService? = nil,
    loggerService: MockHelpStoreLoggerService? = nil,
    scaleService: MockScaleService? = nil,
    bluetoothService: MockHelpStoreBluetoothService? = nil,
    appReviewHandler: MockAppReviewHandler? = nil
// swiftlint:disable:next large_tuple
) -> (
    store: HelpStore,
    notification: MockNotificationHelperService,
    account: MockAccountService,
    logger: MockHelpStoreLoggerService,
    entry: MockHelpStoreEntryService,
    bluetooth: MockHelpStoreBluetoothService,
    appReview: MockAppReviewHandler
) {
    let account = accountService ?? MockAccountService()
    let notification = notificationService ?? MockNotificationHelperService()
    let entry = entryService ?? MockHelpStoreEntryService()
    let logger = loggerService ?? MockHelpStoreLoggerService()
    let scale = scaleService ?? MockScaleService()
    let bluetooth = bluetoothService ?? MockHelpStoreBluetoothService()
    let appReview = appReviewHandler ?? MockAppReviewHandler()

    TestDependencyContainer.reset()
    DependencyContainer.shared.register(account as AccountServiceProtocol)
    DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
    DependencyContainer.shared.register(entry as EntryServiceProtocol)
    DependencyContainer.shared.register(logger as LoggerServiceProtocol)
    DependencyContainer.shared.register(scale as PairedDeviceServiceProtocol)
    DependencyContainer.shared.register(bluetooth as BluetoothServiceProtocol)

    let store = HelpStore(appReviewHandler: appReview)
    store.accountService = account
    store.notificationService = notification
    store.entryService = entry
    store.logger = logger
    store.deviceService = scale
    store.bluetoothService = bluetooth

    return (store, notification, account, logger, entry, bluetooth, appReview)
}

@MainActor
private func waitUntil(
    timeoutNanoseconds: UInt64 = 2_000_000_000,
    pollIntervalNanoseconds: UInt64 = 10_000_000,
    condition: @MainActor () -> Bool
) async -> Bool {
    let start = DispatchTime.now().uptimeNanoseconds
    while DispatchTime.now().uptimeNanoseconds - start < timeoutNanoseconds {
        if condition() { return true }
        try? await Task.sleep(nanoseconds: pollIntervalNanoseconds)
    }
    return false
}
