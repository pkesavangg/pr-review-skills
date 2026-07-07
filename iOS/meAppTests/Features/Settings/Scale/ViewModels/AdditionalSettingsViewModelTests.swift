import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct AdditionalSettingsViewModelTests {

    // MARK: - Helpers

    private func makeSUT(
        connected: Bool = true,
        device: Device? = nil
    // swiftlint:disable:next large_tuple
    ) -> (
        sut: AdditionalSettingsViewModel,
        bluetooth: MockBluetoothService,
        scaleService: MockScaleService,
        notification: MockNotificationHelperService,
        logger: MockLoggerService
    ) {
        TestDependencyContainer.reset()
        let bluetooth = MockBluetoothService()
        let scaleService = MockScaleService()
        let notification = MockNotificationHelperService()
        let logger = MockLoggerService()

        DependencyContainer.shared.register(bluetooth as BluetoothServiceProtocol)
        DependencyContainer.shared.register(scaleService as PairedDeviceServiceProtocol)
        DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)

        let scale = device ?? ScaleTestFixtures.makeDevice(id: "scale-1")
        scaleService.scales = [scale.toSnapshot(isConnected: connected, isWifiConfigured: true)]

        return (AdditionalSettingsViewModel(scale: scale), bluetooth, scaleService, notification, logger)
    }

    // MARK: - Computed properties

    @Test("computed flags read from the published device snapshot")
    func computedFlagsReadFromSnapshot() {
        let (sut, _, _, _, _) = makeSUT(connected: true)

        #expect(sut.isDeviceConnected == true)
        #expect(sut.isWifiConfigured == true)
        #expect(sut.timeFormat == "12")
    }

    @Test("timeFormat falls back to 12 when no snapshot is available")
    func timeFormatFallsBackWhenSnapshotMissing() {
        let (sut, _, scaleService, _, _) = makeSUT(connected: true)
        scaleService.scales = []

        #expect(sut.isDeviceConnected == false)
        #expect(sut.timeFormat == "12")
    }

    // MARK: - getDeviceInfo

    @Test("getDeviceInfo success stores info and animation states")
    func getDeviceInfoSuccess() async {
        let (sut, bluetooth, _, _, _) = makeSUT(connected: true)
        bluetooth.getDeviceInfoResult = .success(
            DeviceInfo(deviceName: "Scale", startAnimationState: true, endAnimationState: false)
        )

        await sut.getDeviceInfo()

        #expect(bluetooth.getDeviceInfoCalls == 1)
        #expect(bluetooth.lastDeviceInfoBroadcastId?.isEmpty == false)
        #expect(sut.deviceInfo?.deviceName == "Scale")
        #expect(sut.startAnimationEnabled == true)
        #expect(sut.endAnimationEnabled == false)
    }

    @Test("getDeviceInfo is skipped when the device is not connected")
    func getDeviceInfoSkippedWhenDisconnected() async {
        let (sut, bluetooth, _, _, _) = makeSUT(connected: false)

        await sut.getDeviceInfo()

        #expect(bluetooth.getDeviceInfoCalls == 0)
        #expect(sut.deviceInfo == nil)
    }

    @Test("getDeviceInfo failure logs an error and leaves info unset")
    func getDeviceInfoFailureLogs() async {
        let (sut, bluetooth, _, _, logger) = makeSUT(connected: true)
        bluetooth.getDeviceInfoResult = .failure(.notImplemented)

        await sut.getDeviceInfo()

        #expect(sut.deviceInfo == nil)
        #expect(logger.entries.contains { $0.level == .error })
    }

    @Test("load calls through to getDeviceInfo")
    func loadCallsGetDeviceInfo() async {
        let (sut, bluetooth, _, _, _) = makeSUT(connected: true)
        bluetooth.getDeviceInfoResult = .success(DeviceInfo(deviceName: "Scale"))

        await sut.load()

        #expect(bluetooth.getDeviceInfoCalls == 1)
    }

    // MARK: - setStartAnimation / setEndAnimation

    @Test("setStartAnimation success sends the setting and updates the flag")
    func setStartAnimationSuccess() async {
        let (sut, bluetooth, _, _, _) = makeSUT(connected: true)
        bluetooth.updateSettingResult = .success(())

        await sut.setStartAnimation(true)

        #expect(bluetooth.updateSettingCalls == 1)
        #expect(bluetooth.lastUpdateSettings.first?.key == "INITIAL_LOGO_ANIM")
        #expect(bluetooth.lastUpdateSettings.first?.value == .bool(true))
        #expect(sut.startAnimationEnabled == true)
    }

    @Test("setStartAnimation failure logs and leaves the flag unchanged")
    func setStartAnimationFailure() async {
        let (sut, bluetooth, _, _, logger) = makeSUT(connected: true)
        bluetooth.updateSettingResult = .failure(.notImplemented)

        await sut.setStartAnimation(true)

        #expect(sut.startAnimationEnabled == false)
        #expect(logger.entries.contains { $0.level == .error })
    }

    @Test("setEndAnimation success sends the setting and updates the flag")
    func setEndAnimationSuccess() async {
        let (sut, bluetooth, _, _, _) = makeSUT(connected: true)
        bluetooth.updateSettingResult = .success(())

        await sut.setEndAnimation(true)

        #expect(bluetooth.lastUpdateSettings.first?.key == "FINAL_LOGO_ANIM")
        #expect(sut.endAnimationEnabled == true)
    }

    @Test("setEndAnimation is skipped when disconnected")
    func setEndAnimationSkippedWhenDisconnected() async {
        let (sut, bluetooth, _, _, _) = makeSUT(connected: false)

        await sut.setEndAnimation(true)

        #expect(bluetooth.updateSettingCalls == 0)
    }

    // MARK: - setTimeFormat

    @Test("setTimeFormat success updates the scale preference and shows a saved toast")
    func setTimeFormatSuccess() async {
        let (sut, bluetooth, scaleService, notification, _) = makeSUT(connected: true)
        bluetooth.updateSettingResult = .success(())

        await sut.setTimeFormat("12H")

        #expect(bluetooth.lastUpdateSettings.first?.key == "TIME_FORMAT")
        #expect(scaleService.updateScalePreferenceFromDTOCalls == 1)
        #expect(scaleService.lastUpdatedScalePreferenceDTO?.timeFormat == "12")
        #expect(notification.toastData?.title == ToastStrings.saved)
    }

    @Test("setTimeFormat maps a non-12H format to the 24h preference value")
    func setTimeFormatMaps24h() async {
        let (sut, bluetooth, scaleService, _, _) = makeSUT(connected: true)
        bluetooth.updateSettingResult = .success(())

        await sut.setTimeFormat("24H")

        #expect(scaleService.lastUpdatedScalePreferenceDTO?.timeFormat == "24")
    }

    @Test("setTimeFormat failure shows an error toast and skips the preference update")
    func setTimeFormatFailure() async {
        let (sut, bluetooth, scaleService, notification, _) = makeSUT(connected: true)
        bluetooth.updateSettingResult = .failure(.notImplemented)

        await sut.setTimeFormat("12H")

        #expect(scaleService.updateScalePreferenceFromDTOCalls == 0)
        #expect(notification.toastData?.title == ToastStrings.error)
    }

    // MARK: - clearData

    @Test("clearData success shows loader, clears, toasts and dismisses the loader")
    func clearDataSuccess() async {
        let (sut, bluetooth, _, notification, _) = makeSUT(connected: true)
        bluetooth.clearDataResult = .success(())

        await sut.clearData(.history)

        #expect(notification.showLoaderCalls == 1)
        #expect(bluetooth.clearDataCalls == 1)
        #expect(bluetooth.lastClearDataType == .history)
        #expect(notification.toastData?.title == ToastStrings.deleted)
        #expect(notification.dismissLoaderCalls == 1)
    }

    @Test("clearData failure logs, shows an error toast and still dismisses the loader")
    func clearDataFailure() async {
        let (sut, bluetooth, _, notification, logger) = makeSUT(connected: true)
        bluetooth.clearDataResult = .failure(.notImplemented)

        await sut.clearData(.all)

        #expect(notification.toastData?.title == ToastStrings.error)
        #expect(notification.dismissLoaderCalls == 1)
        #expect(logger.entries.contains { $0.level == .error })
    }

    @Test("clearData is skipped when the device is not connected")
    func clearDataSkippedWhenDisconnected() async {
        let (sut, bluetooth, _, notification, _) = makeSUT(connected: false)

        await sut.clearData(.all)

        #expect(bluetooth.clearDataCalls == 0)
        #expect(notification.showLoaderCalls == 0)
    }

    // MARK: - resetFirmware / restoreFactorySettings

    @Test("resetFirmware success sends the reset setting and shows a success toast")
    func resetFirmwareSuccess() async {
        let (sut, bluetooth, _, notification, _) = makeSUT(connected: true)
        bluetooth.updateSettingResult = .success(())

        await sut.resetFirmware()

        #expect(bluetooth.lastUpdateSettings.first?.key == "RESET_FIRMWARE")
        #expect(notification.toastData?.title == ToastStrings.success)
    }

    @Test("resetFirmware failure shows an error toast")
    func resetFirmwareFailure() async {
        let (sut, bluetooth, _, notification, _) = makeSUT(connected: true)
        bluetooth.updateSettingResult = .failure(.notImplemented)

        await sut.resetFirmware()

        #expect(notification.toastData?.title == ToastStrings.error)
    }

    @Test("restoreFactorySettings success sends the restore setting and shows a success toast")
    func restoreFactorySettingsSuccess() async {
        let (sut, bluetooth, _, notification, _) = makeSUT(connected: true)
        bluetooth.updateSettingResult = .success(())

        await sut.restoreFactorySettings()

        #expect(bluetooth.lastUpdateSettings.first?.key == "RESTORE_FACTORY")
        #expect(notification.toastData?.title == ToastStrings.success)
    }

    // MARK: - showHelpModal

    @Test("showHelpModal presents a modal")
    func showHelpModalPresentsModal() {
        let (sut, _, _, notification, _) = makeSUT(connected: true)

        sut.showHelpModal()

        #expect(notification.showModalCalls == 1)
    }
}
