import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct SoftwareUpdateViewModelTests {

    private func makeSUT(
        connected: Bool = true,
        currentFirmware: String? = "1.0.0",
        latestVersion: String? = "1.1.0"
    // swiftlint:disable:next large_tuple
    ) -> (
        sut: SoftwareUpdateViewModel,
        bluetooth: MockBluetoothService,
        notification: MockNotificationHelperService,
        logger: MockLoggerService
    ) {
        TestDependencyContainer.reset()
        let bluetooth = MockBluetoothService()
        let notification = MockNotificationHelperService()
        let logger = MockLoggerService()

        DependencyContainer.shared.register(bluetooth as BluetoothServiceProtocol)
        DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)

        let scale = ScaleTestFixtures.makeDevice(id: "scale-1")
        let scaleService = MockScaleService()
        scaleService.scales = [scale.toSnapshot(isConnected: connected)]
        DependencyContainer.shared.register(scaleService as PairedDeviceServiceProtocol)

        let sut = SoftwareUpdateViewModel(
            scale: scale,
            currentFirmware: currentFirmware,
            latestVersion: latestVersion
        )
        return (sut, bluetooth, notification, logger)
    }

    // MARK: - hasUpdate

    @Test("hasUpdate is false when there is no latest version")
    func hasUpdateFalseWithoutLatest() {
        let (sut, _, _, _) = makeSUT(latestVersion: nil)
        #expect(sut.hasUpdate == false)

        let (sut2, _, _, _) = makeSUT(latestVersion: "")
        #expect(sut2.hasUpdate == false)
    }

    @Test("hasUpdate is true when there is a latest version but no current firmware")
    func hasUpdateTrueWhenCurrentMissing() {
        let (sut, _, _, _) = makeSUT(currentFirmware: nil, latestVersion: "2.0.0")
        #expect(sut.hasUpdate == true)

        let (sut2, _, _, _) = makeSUT(currentFirmware: "", latestVersion: "2.0.0")
        #expect(sut2.hasUpdate == true)
    }

    @Test("hasUpdate is false when current firmware already matches the latest version")
    func hasUpdateFalseWhenUpToDate() {
        let (sut, _, _, _) = makeSUT(currentFirmware: "1.1.0", latestVersion: "1.1.0")
        #expect(sut.hasUpdate == false)
    }

    @Test("hasUpdate is true when current firmware differs from the latest version")
    func hasUpdateTrueWhenDifferent() {
        let (sut, _, _, _) = makeSUT(currentFirmware: "1.0.0", latestVersion: "1.1.0")
        #expect(sut.hasUpdate == true)
    }

    // MARK: - updateSoftware

    @Test("updateSoftware immediate success triggers firmware update with a zero timestamp")
    func updateSoftwareImmediateSuccess() async {
        let (sut, bluetooth, notification, _) = makeSUT(connected: true)
        bluetooth.updateFirmwareResult = .success(())

        await sut.updateSoftware(isScheduled: false)

        #expect(bluetooth.updateFirmwareCalls == 1)
        #expect(bluetooth.lastUpdateFirmwareBroadcastId?.isEmpty == false)
        #expect(bluetooth.lastUpdateFirmwareTimestamp == 0)
        #expect(notification.toastData?.title == ToastStrings.success)
        #expect(sut.isUpdating == false)
    }

    @Test("updateSoftware scheduled success sends a non-zero timestamp")
    func updateSoftwareScheduledSuccess() async {
        let (sut, bluetooth, _, _) = makeSUT(connected: true)
        bluetooth.updateFirmwareResult = .success(())
        sut.selectedDate = Date(timeIntervalSince1970: 2_000_000_000)
        sut.selectedTime = Date(timeIntervalSince1970: 2_000_000_000)

        await sut.updateSoftware(isScheduled: true)

        #expect(bluetooth.updateFirmwareCalls == 1)
        #expect((bluetooth.lastUpdateFirmwareTimestamp ?? 0) > 0)
    }

    @Test("updateSoftware failure logs and shows an error toast")
    func updateSoftwareFailure() async {
        let (sut, bluetooth, notification, logger) = makeSUT(connected: true)
        bluetooth.updateFirmwareResult = .failure(.notImplemented)

        await sut.updateSoftware(isScheduled: false)

        #expect(notification.toastData?.title == ToastStrings.error)
        #expect(sut.isUpdating == false)
        #expect(logger.entries.contains { $0.level == .error })
    }

    @Test("updateSoftware is skipped when the device is not connected")
    func updateSoftwareSkippedWhenDisconnected() async {
        let (sut, bluetooth, _, _) = makeSUT(connected: false)

        await sut.updateSoftware(isScheduled: false)

        #expect(bluetooth.updateFirmwareCalls == 0)
        #expect(sut.isUpdating == false)
    }
}
