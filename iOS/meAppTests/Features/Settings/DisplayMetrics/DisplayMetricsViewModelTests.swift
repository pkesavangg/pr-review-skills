import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct DisplayMetricsViewModelTests {
    @Test("initial load orders enabled body and progress metrics from preference")
    func initialLoadOrdersEnabledMetricsFromPreference() {
        let preference = ScaleTestFixtures.makePreferenceDTO(
            displayMetrics: ["musclePercent", "bmi", "goalProgress"],
            shouldMeasureImpedance: true,
            shouldMeasurePulse: true
        )
        let scale = makeScale(preference: preference, isConnected: false)
        let (viewModel, _, _, _, _) = makeSUT(scale: scale)
        let firstTwoEnabled = viewModel.metrics.prefix(2).allSatisfy { $0.isEnabled }

        #expect(viewModel.metrics.prefix(2).map(\.key) == ["musclePercent", "bmi"])
        #expect(firstTwoEnabled == true)
        #expect(viewModel.progressMetrics.first?.key == "goalProgress")
        #expect(viewModel.progressMetrics.first?.isEnabled == true)
        #expect(viewModel.displayMetricsValue == "musclePercent,bmi,goalProgress")
        #expect(viewModel.hasChanges == false)
        #expect(viewModel.showWeightOnlyBanner == false)
        #expect(viewModel.showHeartRateBanner == false)
    }

    @Test("loadDisplayMetricsData applies weight-only and heart-rate banner states")
    func loadDisplayMetricsDataAppliesBannerStates() async {
        let preference = ScaleTestFixtures.makePreferenceDTO(
            displayMetrics: ["bmi", "heartRate"],
            shouldMeasureImpedance: true,
            shouldMeasurePulse: false
        )
        let scale = makeScale(preference: preference, isConnected: false)
        let (viewModel, logger, _, _, _) = makeSUT(scale: scale, isWeighOnlyModeEnabledByOthers: true)

        await viewModel.loadDisplayMetricsData()

        let heartRateMetric = viewModel.metrics.first { $0.key == "heartRate" }
        #expect(viewModel.showWeightOnlyInfo == true)
        #expect(viewModel.showWeightOnlyBanner == false)
        #expect(viewModel.showHeartRateBanner == true)
        #expect(viewModel.isHeartRateOn == false)
        #expect(heartRateMetric?.isEnabled == false)
        #expect(viewModel.displayMetricsValue.contains("heartRate") == false)
        #expect(logger.messages.contains { $0.contains("cannot be enabled") } == false)
    }

    @Test("loadDisplayMetricsData without preference falls back to defaults")
    func loadDisplayMetricsDataWithoutPreferenceUsesDefaults() async {
        let scale = makeScale(preference: nil, isConnected: false)
        let (viewModel, _, _, _, _) = makeSUT(scale: scale)

        await viewModel.loadDisplayMetricsData()

        #expect(viewModel.metrics.map(\.key) == ScaleMetrics.bodyMetrics.map(\.key))
        #expect(viewModel.progressMetrics.map(\.key) == ScaleMetrics.progressMetrics.map(\.key))
        #expect(viewModel.showWeightOnlyBanner == false)
        #expect(viewModel.showHeartRateBanner == false)
        #expect(viewModel.hasChanges == false)
    }

    @Test("weight-only preference shows only BMI body metric and weight-only banner")
    func weightOnlyPreferenceShowsOnlyBMIMetric() async {
        let preference = ScaleTestFixtures.makePreferenceDTO(
            displayMetrics: ["bmi", "goalProgress"],
            shouldMeasureImpedance: false,
            shouldMeasurePulse: false
        )
        let scale = makeScale(preference: preference, isConnected: false)
        let (viewModel, _, _, _, _) = makeSUT(scale: scale)

        await viewModel.loadDisplayMetricsData()

        #expect(viewModel.metrics.map(\.key) == ["bmi"])
        #expect(viewModel.showWeightOnlyBanner == true)
        #expect(viewModel.isWeightOnlyModeOn == true)
    }

    @Test("updateMetrics marks changes and updates combined displayMetricsValue")
    func updateMetricsMarksChangesAndUpdatesValue() {
        let preference = ScaleTestFixtures.makePreferenceDTO(displayMetrics: ["bmi"], shouldMeasureImpedance: true)
        let scale = makeScale(preference: preference, isConnected: false)
        let (viewModel, _, _, _, _) = makeSUT(scale: scale)
        var updated = viewModel.metrics
        if let bmiIndex = updated.firstIndex(where: { $0.key == "bmi" }) {
            updated[bmiIndex].isEnabled = true
        }
        if let bodyFatIndex = updated.firstIndex(where: { $0.key == "bodyFatPercent" }) {
            updated[bodyFatIndex].isEnabled = true
        }

        viewModel.updateMetrics(updated)

        #expect(viewModel.hasChanges == true)
        #expect(viewModel.displayMetricsValue.contains("bmi"))
        #expect(viewModel.displayMetricsValue.contains("bodyFatPercent"))
    }

    @Test("updateMetrics reorder operation preserves enabled keys and marks changes")
    func updateMetricsReorderOperationMarksChanges() {
        let preference = ScaleTestFixtures.makePreferenceDTO(
            displayMetrics: ["bmi", "bodyFatPercent"],
            shouldMeasureImpedance: true
        )
        let scale = makeScale(preference: preference, isConnected: false)
        let (viewModel, _, _, _, _) = makeSUT(scale: scale)
        var reordered = viewModel.metrics
        let first = reordered.removeFirst()
        reordered.insert(first, at: 1)

        viewModel.updateMetrics(reordered)

        #expect(viewModel.hasChanges == true)
        #expect(Set(viewModel.metrics.filter(\.isEnabled).map(\.key)) == Set(["bmi", "bodyFatPercent"]))
    }

    @Test("updateProgressMetrics reorder operation preserves enabled set and marks changes")
    func updateProgressMetricsReorderOperationMarksChanges() {
        let preference = ScaleTestFixtures.makePreferenceDTO(
            displayMetrics: ["goalProgress", "weeklyAverage"],
            shouldMeasureImpedance: true
        )
        let scale = makeScale(preference: preference, isConnected: false)
        let (viewModel, _, _, _, _) = makeSUT(scale: scale)
        var reordered = viewModel.progressMetrics
        let first = reordered.removeFirst()
        reordered.insert(first, at: 1)

        viewModel.updateProgressMetrics(reordered)

        #expect(viewModel.hasChanges == true)
        #expect(Set(viewModel.progressMetrics.filter(\.isEnabled).map(\.key)) == Set(["goalProgress", "weeklyAverage"]))
    }

    @Test("updateProgressMetrics toggle operation updates value")
    func updateProgressMetricsToggleOperationUpdatesValue() {
        let preference = ScaleTestFixtures.makePreferenceDTO(
            displayMetrics: ["goalProgress"],
            shouldMeasureImpedance: true
        )
        let scale = makeScale(preference: preference, isConnected: false)
        let (viewModel, _, _, _, _) = makeSUT(scale: scale)
        var updated = viewModel.progressMetrics
        if let monthlyIndex = updated.firstIndex(where: { $0.key == "monthlyAverage" }) {
            updated[monthlyIndex].isEnabled = true
        }

        viewModel.updateProgressMetrics(updated)

        #expect(viewModel.hasChanges == true)
        #expect(viewModel.displayMetricsValue.contains("monthlyAverage"))
    }

    @Test("handleBodyMetricToggle blocks enabling heart rate when banner is shown")
    func handleBodyMetricToggleBlocksHeartRateWhenBannerShown() {
        let preference = ScaleTestFixtures.makePreferenceDTO(
            displayMetrics: ["bmi"],
            shouldMeasureImpedance: true,
            shouldMeasurePulse: false
        )
        let scale = makeScale(preference: preference, isConnected: false)
        let (viewModel, logger, _, _, _) = makeSUT(scale: scale)

        viewModel.handleBodyMetricToggle(key: "heartRate", isEnabled: true)

        #expect(viewModel.metrics.first(where: { $0.key == "heartRate" })?.isEnabled == false)
        #expect(logger.messages.contains { $0.contains("Heart rate cannot be enabled") })
    }

    @Test("handleBodyMetricToggle allowed path updates and reorders asynchronously")
    func handleBodyMetricToggleAllowedPathUpdatesAndReorders() async {
        let preference = ScaleTestFixtures.makePreferenceDTO(
            displayMetrics: ["bmi", "bodyFatPercent"],
            shouldMeasureImpedance: true,
            shouldMeasurePulse: true
        )
        let scale = makeScale(preference: preference, isConnected: false)
        let (viewModel, _, _, _, _) = makeSUT(scale: scale)

        viewModel.handleBodyMetricToggle(key: "bmi", isEnabled: false)
        await Task.yield()

        #expect(viewModel.hasChanges == true)
        #expect(viewModel.metrics.last?.key == "bmi")
        #expect(viewModel.metrics.last?.isEnabled == false)
    }

    @Test("handleProgressMetricToggle blocked heart-rate path logs and leaves state unchanged")
    func handleProgressMetricToggleBlockedHeartRatePath() {
        let preference = ScaleTestFixtures.makePreferenceDTO(
            displayMetrics: ["bmi"],
            shouldMeasureImpedance: true,
            shouldMeasurePulse: false
        )
        let scale = makeScale(preference: preference, isConnected: false)
        let (viewModel, logger, _, _, _) = makeSUT(scale: scale)
        let original = viewModel.progressMetrics

        viewModel.handleProgressMetricToggle(key: "heartRate", isEnabled: true)

        #expect(viewModel.progressMetrics.map(\.key) == original.map(\.key))
        #expect(logger.messages.contains { $0.contains("Heart rate cannot be enabled") })
    }

    @Test("handleProgressMetricToggle allowed path updates and reorders asynchronously")
    func handleProgressMetricToggleAllowedPathUpdatesAndReorders() async {
        let preference = ScaleTestFixtures.makePreferenceDTO(
            displayMetrics: ["goalProgress", "weeklyAverage"],
            shouldMeasureImpedance: true,
            shouldMeasurePulse: true
        )
        let scale = makeScale(preference: preference, isConnected: false)
        let (viewModel, _, _, _, _) = makeSUT(scale: scale)

        viewModel.handleProgressMetricToggle(key: "goalProgress", isEnabled: false)
        await Task.yield()

        #expect(viewModel.hasChanges == true)
        #expect(viewModel.progressMetrics.last?.key == "goalProgress")
        #expect(viewModel.progressMetrics.last?.isEnabled == false)
    }

    @Test("saveDisplayMetrics success updates preference and shows success toast")
    func saveDisplayMetricsSuccessUpdatesPreference() async {
        let preference = ScaleTestFixtures.makePreferenceDTO(displayMetrics: ["bmi"], shouldMeasureImpedance: true)
        let scale = makeScale(preference: preference, isConnected: false)
        let (viewModel, _, notification, scaleService, _) = makeSUT(scale: scale)

        var body = viewModel.metrics
        var progress = viewModel.progressMetrics
        body = setEnabled(keys: ["bmi", "bodyFatPercent"], in: body)
        progress = setEnabled(keys: ["weeklyAverage"], in: progress)
        viewModel.updateMetrics(body)
        viewModel.updateProgressMetrics(progress)

        await viewModel.saveDisplayMetrics()

        #expect(scaleService.updateScalePreferenceFromDTOCalls == 1)
        #expect(scaleService.pushLocalChangesToServerCalls == 1)
        #expect(scaleService.lastUpdatedScalePreferenceDTO?.displayMetrics == ["bmi", "bodyFatPercent", "weeklyAverage"])
        #expect(notification.showLoaderCalls == 1)
        #expect(notification.dismissLoaderCalls == 1)
        #expect(notification.toastData?.title == ToastStrings.success)
        #expect(notification.toastData?.message == ToastStrings.displayMetricsSaved)
        #expect(viewModel.hasChanges == false)
    }

    @Test("saveDisplayMetrics excludes heartRate when pulse is off")
    func saveDisplayMetricsExcludesHeartRateWhenPulseIsOff() async {
        let preference = ScaleTestFixtures.makePreferenceDTO(
            displayMetrics: ["bmi", "heartRate", "goalProgress"],
            shouldMeasureImpedance: true,
            shouldMeasurePulse: false
        )
        let scale = makeScale(preference: preference, isConnected: false)
        let (viewModel, _, _, scaleService, _) = makeSUT(scale: scale)

        await viewModel.saveDisplayMetrics()

        let savedMetrics = scaleService.lastUpdatedScalePreferenceDTO?.displayMetrics ?? []
        #expect(savedMetrics.contains("heartRate") == false)
    }

    @Test("saveDisplayMetrics connected scale syncs metrics over bluetooth")
    func saveDisplayMetricsConnectedScaleSyncsOverBluetooth() async {
        let preference = ScaleTestFixtures.makePreferenceDTO(displayMetrics: ["bmi"], shouldMeasureImpedance: true)
        let scale = makeScale(preference: preference, isConnected: true)
        let bluetooth = MockBluetoothService()
        bluetooth.updateAccountResult = .success(.creationCompleted)
        let (viewModel, _, _, scaleService, bluetoothService) = makeSUT(scale: scale, bluetooth: bluetooth)

        await viewModel.saveDisplayMetrics()

        #expect(scaleService.updateScalePreferenceFromDTOCalls == 1)
        #expect(bluetoothService.updateAccountCalls == 1)
    }

    @Test("saveDisplayMetrics in weight-only mode updates BMI and progress while preserving non-progress metrics")
    func saveDisplayMetricsWeightOnlyModePreservesNonProgressMetrics() async {
        let preference = ScaleTestFixtures.makePreferenceDTO(
            displayMetrics: ["bodyFatPercent", "goalProgress"],
            shouldMeasureImpedance: false,
            shouldMeasurePulse: false
        )
        let scale = makeScale(preference: preference, isConnected: false)
        let (viewModel, _, _, scaleService, _) = makeSUT(scale: scale)

        var body = viewModel.metrics
        var progress = viewModel.progressMetrics
        body = setEnabled(keys: ["bmi"], in: body)
        progress = setEnabled(keys: ["weeklyAverage"], in: progress)
        viewModel.updateMetrics(body)
        viewModel.updateProgressMetrics(progress)

        await viewModel.saveDisplayMetrics()

        #expect(scaleService.lastUpdatedScalePreferenceDTO?.displayMetrics == ["bodyFatPercent", "bmi", "weeklyAverage"])
    }

    @Test("saveDisplayMetrics in weight-only mode removes BMI when disabled")
    func saveDisplayMetricsWeightOnlyModeRemovesBMIDisabled() async {
        let preference = ScaleTestFixtures.makePreferenceDTO(
            displayMetrics: ["bmi", "bodyFatPercent", "goalProgress"],
            shouldMeasureImpedance: false,
            shouldMeasurePulse: false
        )
        let scale = makeScale(preference: preference, isConnected: false)
        let (viewModel, _, _, scaleService, _) = makeSUT(scale: scale)

        var body = viewModel.metrics
        var progress = viewModel.progressMetrics
        body = setEnabled(keys: [], in: body) // BMI disabled
        progress = setEnabled(keys: ["monthlyAverage"], in: progress)
        viewModel.updateMetrics(body)
        viewModel.updateProgressMetrics(progress)

        await viewModel.saveDisplayMetrics()

        let saved = scaleService.lastUpdatedScalePreferenceDTO?.displayMetrics ?? []
        #expect(saved.contains("bmi") == false)
        #expect(saved == ["bodyFatPercent", "monthlyAverage"])
    }

    @Test("saveDisplayMetrics failure while updating scale preference shows error toast")
    func saveDisplayMetricsUpdateFailureShowsErrorToast() async {
        let preference = ScaleTestFixtures.makePreferenceDTO(displayMetrics: ["bmi"], shouldMeasureImpedance: true)
        let scale = makeScale(preference: preference, isConnected: false)
        let scaleService = MockScaleService()
        scaleService.updateScalePreferenceError = ScaleTestError.localFailure
        let (viewModel, _, notification, _, _) = makeSUT(scale: scale, scaleService: scaleService)

        await viewModel.saveDisplayMetrics()

        #expect(notification.showLoaderCalls == 1)
        #expect(notification.dismissLoaderCalls == 1)
        #expect(notification.toastData?.title == ToastStrings.error)
        #expect(notification.toastData?.message == ToastStrings.errorSavingDisplayMetrics)
    }

    @Test("saveDisplayMetrics bluetooth sync failure shows error toast")
    func saveDisplayMetricsBluetoothFailureShowsErrorToast() async {
        let preference = ScaleTestFixtures.makePreferenceDTO(displayMetrics: ["bmi"], shouldMeasureImpedance: true)
        let scale = makeScale(preference: preference, isConnected: true)
        let bluetooth = MockBluetoothService()
        bluetooth.updateAccountResult = .failure(.notImplemented)
        let (viewModel, _, notification, scaleService, _) = makeSUT(scale: scale, bluetooth: bluetooth)

        await viewModel.saveDisplayMetrics()

        #expect(scaleService.updateScalePreferenceFromDTOCalls == 1)
        #expect(notification.dismissLoaderCalls == 1)
        #expect(notification.toastData?.title == ToastStrings.error)
        #expect(notification.toastData?.message == ToastStrings.errorSavingDisplayMetrics)
    }

    @Test("saveDisplayMetrics without preference returns without side effects")
    func saveDisplayMetricsWithoutPreferenceReturnsEarly() async {
        let scale = makeScale(preference: nil, isConnected: false)
        let (viewModel, _, notification, scaleService, bluetoothService) = makeSUT(scale: scale)

        await viewModel.saveDisplayMetrics()

        #expect(notification.showLoaderCalls == 0)
        #expect(scaleService.updateScalePreferenceFromDTOCalls == 0)
        #expect(bluetoothService.updateAccountCalls == 0)
    }

    @Test("init with DI fallback resolves dependencies from container")
    func initWithDIFallbackResolvesDependenciesFromContainer() {
        TestDependencyContainer.reset()
        let logger = MockLoggerService()
        let notification = MockNotificationHelperService()
        let scaleService = MockScaleService()
        let bluetooth = MockBluetoothService()
        let account = MockAccountService()
        let scale = makeScale(preference: ScaleTestFixtures.makePreferenceDTO(), isConnected: false)

        DependencyContainer.shared.register(logger as LoggerServiceProtocol)
        DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
        DependencyContainer.shared.register(scaleService as ScaleServiceProtocol)
        DependencyContainer.shared.register(bluetooth as BluetoothServiceProtocol)
        DependencyContainer.shared.register(account as AccountServiceProtocol)

        let viewModel = DisplayMetricsViewModel(scale: scale)

        #expect((viewModel.notificationService as? MockNotificationHelperService) === notification)
        #expect((viewModel.scaleService as? MockScaleService) === scaleService)
        #expect((viewModel.bluetoothService as? MockBluetoothService) === bluetooth)
    }

    @Test("loadDisplayMetricsData reflects the latest snapshot published by ScaleService")
    func loadDisplayMetricsDataReflectsLatestSnapshot() async {
        let initial = makeScale(
            preference: ScaleTestFixtures.makePreferenceDTO(
                displayMetrics: ["bmi"],
                shouldMeasureImpedance: true
            ),
            isConnected: false
        )
        let scaleService = MockScaleService()
        let (viewModel, _, _, _, _) = makeSUT(scale: initial, scaleService: scaleService)

        let fresh = ScaleTestFixtures.makeDevice(id: initial.id, accountId: initial.accountId)
        fresh.r4ScalePreference = R4ScalePreference(
            from: ScaleTestFixtures.makePreferenceDTO(
                displayMetrics: ["bodyFatPercent", "goalProgress"],
                shouldMeasureImpedance: true
            ),
            scaleId: initial.id
        )
        scaleService.scales = [fresh.toSnapshot(isConnected: false)]

        await viewModel.loadDisplayMetricsData()

        #expect(viewModel.displayMetricsValue == "bodyFatPercent,goalProgress")
    }

    private func makeSUT(
        scale: Device,
        isWeighOnlyModeEnabledByOthers: Bool = false,
        logger: MockLoggerService? = nil,
        notification: MockNotificationHelperService? = nil,
        scaleService: MockScaleService? = nil,
        bluetooth: MockBluetoothService? = nil,
        accountService: MockAccountService? = nil
    // swiftlint:disable:next large_tuple
    ) -> (
        viewModel: DisplayMetricsViewModel,
        logger: MockLoggerService,
        notification: MockNotificationHelperService,
        scaleService: MockScaleService,
        bluetoothService: MockBluetoothService
    ) {
        let logger = logger ?? MockLoggerService()
        let notification = notification ?? MockNotificationHelperService()
        let scaleService = scaleService ?? MockScaleService()
        let bluetooth = bluetooth ?? MockBluetoothService()
        let accountService = accountService ?? MockAccountService()

        // Publish the scale as a DeviceSnapshot so the ViewModel can resolve it via ScaleService.
        scaleService.scales = [scale.toSnapshot(isConnected: scale.isConnected ?? false)]

        let viewModel = DisplayMetricsViewModel(
            scale: scale,
            isWeighOnlyModeEnabledByOthers: isWeighOnlyModeEnabledByOthers,
            notificationService: notification,
            scaleService: scaleService,
            bluetoothService: bluetooth,
            logger: logger,
            accountService: accountService
        )
        return (viewModel, logger, notification, scaleService, bluetooth)
    }

    private func makeScale(
        preference: R4ScalePreferenceDTO?,
        isConnected: Bool
    ) -> Device {
        let scale = ScaleTestFixtures.makeDevice(id: "display-scale-1", accountId: "acct-1", displayName: "Display Scale")
        scale.isConnected = isConnected
        if let preference {
            scale.r4ScalePreference = R4ScalePreference(from: preference, scaleId: scale.id)
        } else {
            scale.r4ScalePreference = nil
        }
        return scale
    }

    private func setEnabled(keys: Set<String>, in source: [ScaleMetricSetting]) -> [ScaleMetricSetting] {
        source.map { metric in
            var updated = metric
            updated.isEnabled = keys.contains(metric.key)
            return updated
        }
    }
}
