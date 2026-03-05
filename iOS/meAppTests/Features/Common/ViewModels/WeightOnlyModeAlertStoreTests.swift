import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct WeightOnlyModeAlertStoreTests {
    @Test("initial state starts empty and not loading")
    func initialStateStartsEmpty() {
        let (store, _, _, _) = makeSUT()

        #expect(store.isLoading == false)
        #expect(store.weightOnlyScales.isEmpty)
        #expect(store.showingScaleList == false)
    }

    @Test("loadWeightOnlyScales filters only scales enabled by others")
    func loadWeightOnlyScalesFiltersEnabledByOthers() async {
        let scaleService = MockScaleService()
        scaleService.scales = [
            makeScale(id: "enabled", isConnected: true, isWeightOnlyEnabledByOthers: true),
            makeScale(id: "disabled", isConnected: true, isWeightOnlyEnabledByOthers: false),
            makeScale(id: "unknown", isConnected: true, isWeightOnlyEnabledByOthers: nil)
        ]
        let (store, _, _, _) = makeSUT(scaleService: scaleService)

        store.loadWeightOnlyScales()
        let loaded = await waitUntil {
            store.isLoading == false && store.weightOnlyScales.count == 1
        }

        #expect(loaded == true)
        #expect(store.weightOnlyScales.map(\.id) == ["enabled"])
    }

    @Test("loadWeightOnlyScales failure clears scales and stops loading")
    func loadWeightOnlyScalesFailureClearsState() async {
        let scaleService = MockScaleService()
        scaleService.scales = [makeScale(id: "cached", isConnected: true, isWeightOnlyEnabledByOthers: true)]
        scaleService.getDevicesError = WeightOnlyModeAlertStoreTestError.loadFailed
        let (store, _, _, _) = makeSUT(scaleService: scaleService)
        store.weightOnlyScales = [makeScale(id: "stale", isConnected: true, isWeightOnlyEnabledByOthers: true)]

        store.loadWeightOnlyScales()
        let loaded = await waitUntil { store.isLoading == false }

        #expect(loaded == true)
        #expect(store.weightOnlyScales.isEmpty)
    }

    @Test("device discovery observer reloads scales each time with latest data")
    func deviceDiscoveryObserverReloadsScalesRepeatedly() async {
        let scaleService = MockScaleService()
        let bluetooth = MockBluetoothService()
        let (store, _, _, _) = makeSUT(scaleService: scaleService, bluetooth: bluetooth)
        let event = BtWifiStoreTestFixtures.makeDiscoveryEvent(
            scale: makeScale(id: "discovered", isConnected: true, isWeightOnlyEnabledByOthers: true)
        )

        scaleService.scales = [makeScale(id: "first", isConnected: true, isWeightOnlyEnabledByOthers: true)]
        bluetooth.deviceDiscoveredSubject.send(event)
        let firstLoaded = await waitUntil { store.weightOnlyScales.map(\.id) == ["first"] && store.isLoading == false }

        scaleService.scales = [
            makeScale(id: "second", isConnected: true, isWeightOnlyEnabledByOthers: true),
            makeScale(id: "filtered-out", isConnected: true, isWeightOnlyEnabledByOthers: false)
        ]
        bluetooth.deviceDiscoveredSubject.send(event)
        let secondLoaded = await waitUntil { store.weightOnlyScales.map(\.id) == ["second"] && store.isLoading == false }

        #expect(firstLoaded == true)
        #expect(secondLoaded == true)
    }

    @Test("enableBodyMetricsForScale shows expected alert message and buttons")
    func enableBodyMetricsForScaleShowsExpectedAlert() {
        let (store, _, _, notification) = makeSUT()

        store.enableBodyMetricsForScale()

        #expect(notification.showAlertCalls == 1)
        #expect(notification.alertData?.title == AlertStrings.EnableBodyMetricsAlert.title)
        #expect(notification.alertData?.message == AlertStrings.EnableBodyMetricsAlert.message)
        #expect(notification.alertData?.buttons.map(\.title) == [
            AlertStrings.EnableBodyMetricsAlert.enableButton,
            AlertStrings.EnableBodyMetricsAlert.cancelButton
        ])
    }

    @Test("enableBodyMetricsForScale cancel action invokes callback and skips update flow")
    func enableBodyMetricsForScaleCancelRunsCallbackOnly() async {
        let (store, _, bluetooth, notification) = makeSUT()
        var cancelCalls = 0

        store.enableBodyMetricsForScale {
            cancelCalls += 1
        }
        notification.alertData?.buttons.last?.action(nil)
        await Task.yield()

        #expect(cancelCalls == 1)
        #expect(bluetooth.updateWeightOnlyModeCalls == 0)
        #expect(notification.showLoaderCalls == 0)
        #expect(notification.showToastCalls == 0)
    }

    @Test("enableBodyMetricsForScale primary action runs success flow")
    func enableBodyMetricsForScalePrimaryActionRunsSuccessFlow() async {
        let bluetooth = MockBluetoothService()
        bluetooth.updateWeightOnlyModeResult = .success(())
        let (store, _, _, notification) = makeSUT(bluetooth: bluetooth)
        store.weightOnlyScales = [makeScale(id: "connected", isConnected: true, isWeightOnlyEnabledByOthers: true)]

        store.enableBodyMetricsForScale()
        notification.alertData?.buttons.first?.action(nil)
        let completed = await waitUntil {
            bluetooth.updateWeightOnlyModeCalls == 1 &&
            notification.dismissLoaderCalls == 1
        }

        #expect(completed == true)
        #expect(notification.showLoaderCalls == 1)
        #expect(notification.showToastCalls == 1)
        #expect(notification.toastData?.message == WeightOnlyModeStrings.temporaryOverride)
        #expect(bluetooth.lastWeightOnlyModeDevice == nil)
    }

    @Test("enableBodyMetricsForScale can be shown repeatedly with consistent content")
    func enableBodyMetricsForScaleCanBeShownRepeatedly() {
        let (store, _, _, notification) = makeSUT()

        store.enableBodyMetricsForScale()
        store.enableBodyMetricsForScale()

        #expect(notification.showAlertCalls == 2)
        #expect(notification.alertData?.title == AlertStrings.EnableBodyMetricsAlert.title)
        #expect(notification.alertData?.buttons.map(\.title) == [
            AlertStrings.EnableBodyMetricsAlert.enableButton,
            AlertStrings.EnableBodyMetricsAlert.cancelButton
        ])
    }

    @Test("handleEnableBodyMetrics with no connected scales skips loader and toast")
    func handleEnableBodyMetricsNoConnectedScalesDoesNothing() async {
        let (store, _, bluetooth, notification) = makeSUT()
        store.weightOnlyScales = [
            makeScale(id: "disconnected", isConnected: false, isWeightOnlyEnabledByOthers: true)
        ]

        store.handleEnableBodyMetrics()
        await Task.yield()
        await Task.yield()

        #expect(bluetooth.updateWeightOnlyModeCalls == 0)
        #expect(notification.showLoaderCalls == 0)
        #expect(notification.dismissLoaderCalls == 0)
        #expect(notification.showToastCalls == 0)
    }

    @Test("handleEnableBodyMetrics failure shows error toast")
    func handleEnableBodyMetricsFailureShowsErrorToast() async {
        let bluetooth = MockBluetoothService()
        bluetooth.updateWeightOnlyModeResult = .failure(.notImplemented)
        let (store, _, _, notification) = makeSUT(bluetooth: bluetooth)
        store.weightOnlyScales = [makeScale(id: "connected", isConnected: true, isWeightOnlyEnabledByOthers: true)]

        store.handleEnableBodyMetrics()
        let completed = await waitUntil {
            bluetooth.updateWeightOnlyModeCalls == 1 &&
            notification.dismissLoaderCalls == 1
        }

        #expect(completed == true)
        #expect(notification.showLoaderCalls == 1)
        #expect(notification.showToastCalls == 1)
        #expect(notification.toastData?.title == WeightOnlyModeStrings.enableFailedTitle)
        #expect(notification.toastData?.message == WeightOnlyModeStrings.enableFailedMessage)
    }

    @Test("dismissWeightOnlyModeAlert shows expected message and dismiss action calls bluetooth service")
    func dismissWeightOnlyModeAlertShowsExpectedAlertAndRunsDismiss() {
        let (store, _, bluetooth, notification) = makeSUT()

        store.dismissWeightOnlyModeAlert()
        notification.alertData?.buttons.first?.action(nil)

        #expect(notification.showAlertCalls == 1)
        #expect(notification.alertData?.title == AlertStrings.DisableWeightOnlyModeAlert.title)
        #expect(notification.alertData?.message == AlertStrings.DisableWeightOnlyModeAlert.message)
        #expect(notification.alertData?.buttons.map(\.title) == [
            AlertStrings.DisableWeightOnlyModeAlert.dismissButton,
            AlertStrings.DisableWeightOnlyModeAlert.cancelButton
        ])
        #expect(bluetooth.handleWeightOnlyModeAlertDismissedCalls == 1)
    }

    @Test("dismissWeightOnlyModeAlert cancel action invokes callback")
    func dismissWeightOnlyModeAlertCancelRunsCallback() {
        let (store, _, bluetooth, notification) = makeSUT()
        var cancelCalls = 0

        store.dismissWeightOnlyModeAlert {
            cancelCalls += 1
        }
        notification.alertData?.buttons.last?.action(nil)

        #expect(cancelCalls == 1)
        #expect(bluetooth.handleWeightOnlyModeAlertDismissedCalls == 0)
    }

    @Test("dismissWeightOnlyModeAlert supports repeated dismisses")
    func dismissWeightOnlyModeAlertSupportsRepeatedDismisses() {
        let (store, _, bluetooth, notification) = makeSUT()

        store.dismissWeightOnlyModeAlert()
        notification.alertData?.buttons.first?.action(nil)
        store.dismissWeightOnlyModeAlert()
        notification.alertData?.buttons.first?.action(nil)

        #expect(notification.showAlertCalls == 2)
        #expect(bluetooth.handleWeightOnlyModeAlertDismissedCalls == 2)
    }

    private func makeSUT(
        scaleService: MockScaleService? = nil,
        bluetooth: MockBluetoothService? = nil,
        notification: MockNotificationHelperService? = nil
    ) -> (WeightOnlyModeAlertStore, MockScaleService, MockBluetoothService, MockNotificationHelperService) {
        let scaleService = scaleService ?? MockScaleService()
        let bluetooth = bluetooth ?? MockBluetoothService()
        let notification = notification ?? MockNotificationHelperService()

        let store = WeightOnlyModeAlertStore(
            scaleService: scaleService,
            bluetoothService: bluetooth,
            notificationService: notification
        )
        return (store, scaleService, bluetooth, notification)
    }

    private func makeScale(
        id: String,
        isConnected: Bool,
        isWeightOnlyEnabledByOthers: Bool?
    ) -> Device {
        let scale = ScaleTestFixtures.makeDevice(id: id, accountId: "acct-1", displayName: id)
        scale.isConnected = isConnected
        scale.isWeighOnlyModeEnabledByOthers = isWeightOnlyEnabledByOthers
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

private enum WeightOnlyModeAlertStoreTestError: Error {
    case loadFailed
}
