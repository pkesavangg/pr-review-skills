import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct DeviceNameViewModelTests {

    private static let originalName = "Original"

    private func makeSUT(
        nickname: String = originalName
    // swiftlint:disable:next large_tuple
    ) -> (
        sut: DeviceNameViewModel,
        scaleService: MockScaleService,
        notification: MockNotificationHelperService,
        logger: MockLoggerService
    ) {
        TestDependencyContainer.reset()
        let scaleService = MockScaleService()
        let notification = MockNotificationHelperService()
        let logger = MockLoggerService()

        DependencyContainer.shared.register(scaleService as PairedDeviceServiceProtocol)
        DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)

        let scale = ScaleTestFixtures.makeDevice(id: "scale-1")
        scale.nickname = nickname
        scaleService.scales = [scale.toSnapshot(isConnected: true)]

        return (DeviceNameViewModel(scale: scale), scaleService, notification, logger)
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

    // MARK: - saveDeviceName

    @Test("saveDeviceName success edits, pushes, toasts and runs the success callback")
    func saveDeviceNameSuccess() async {
        let (sut, scaleService, notification, _) = makeSUT()
        var succeeded = 0

        await sut.saveDeviceName("New Name") { succeeded += 1 }

        #expect(notification.showLoaderCalls == 1)
        #expect(scaleService.editDeviceCalls == 1)
        #expect(scaleService.lastEditDeviceProperties?["nickname"] as? String == "New Name")
        #expect(scaleService.pushLocalChangesToServerCalls == 1)
        #expect(notification.toastData?.title == ToastStrings.success)
        #expect(notification.toastData?.message == ToastStrings.scaleNameUpdated)
        #expect(succeeded == 1)
        #expect(notification.dismissLoaderCalls == 1)
    }

    @Test("saveDeviceName failure shows an error toast and skips the success callback")
    func saveDeviceNameFailure() async {
        let (sut, scaleService, notification, logger) = makeSUT()
        scaleService.editDeviceError = ScaleTestError.localFailure
        var succeeded = 0

        await sut.saveDeviceName("New Name") { succeeded += 1 }

        #expect(scaleService.pushLocalChangesToServerCalls == 0)
        #expect(notification.toastData?.title == ToastStrings.errorEditingScale)
        #expect(notification.toastData?.message == ToastStrings.restartApp)
        #expect(succeeded == 0)
        #expect(notification.dismissLoaderCalls == 1)
        #expect(logger.entries.contains { $0.level == .error })
    }

    // MARK: - isDirtyComparedToOriginal

    @Test("isDirtyComparedToOriginal is false for the unchanged name")
    func isDirtyFalseWhenUnchanged() {
        let (sut, _, _, _) = makeSUT()

        #expect(sut.isDirtyComparedToOriginal(editedName: Self.originalName) == false)
    }

    @Test("isDirtyComparedToOriginal ignores surrounding whitespace")
    func isDirtyIgnoresWhitespace() {
        let (sut, _, _, _) = makeSUT()

        #expect(sut.isDirtyComparedToOriginal(editedName: "  \(Self.originalName)  ") == false)
    }

    @Test("isDirtyComparedToOriginal is true for a changed name")
    func isDirtyTrueWhenChanged() {
        let (sut, _, _, _) = makeSUT()

        #expect(sut.isDirtyComparedToOriginal(editedName: "Different") == true)
    }

    // MARK: - allowExit

    @Test("allowExit returns true immediately when the form is not dirty")
    func allowExitNotDirty() async {
        let (sut, _, notification, _) = makeSUT()

        let allowed = await sut.allowExit(isFormDirty: false, editedName: "Different")

        #expect(allowed == true)
        #expect(notification.showAlertCalls == 0)
    }

    @Test("allowExit returns true when the edited name equals the original despite dirty flag")
    func allowExitDirtyButUnchanged() async {
        let (sut, _, notification, _) = makeSUT()

        let allowed = await sut.allowExit(isFormDirty: true, editedName: Self.originalName)

        #expect(allowed == true)
        #expect(notification.showAlertCalls == 0)
    }

    @Test("allowExit prompts confirmation when dirty and changed; exit confirms")
    func allowExitDirtyChangedConfirmsExit() async {
        let (sut, _, notification, _) = makeSUT()

        let task = Task { await sut.allowExit(isFormDirty: true, editedName: "Changed") }
        _ = await waitUntil { notification.showAlertCalls == 1 }
        notification.alertData?.buttons.first?.action(nil)

        let allowed = await task.value
        #expect(allowed == true)
    }

    @Test("allowExit prompts confirmation when dirty and changed; return cancels")
    func allowExitDirtyChangedCancels() async {
        let (sut, _, notification, _) = makeSUT()

        let task = Task { await sut.allowExit(isFormDirty: true, editedName: "Changed") }
        _ = await waitUntil { notification.showAlertCalls == 1 }
        notification.alertData?.buttons.last?.action(nil)

        let allowed = await task.value
        #expect(allowed == false)
    }
}
