import Foundation
import GGBluetoothSwiftPackage
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct AppSyncSetupStoreTests {
    @Test("configure for body composition scale keeps addInfo and permissions when camera is disabled")
    func configureBodyCompScaleIncludesAddInfoAndPermissions() {
        let (store, _, permissions, _, _, _) = makeSUT()
        permissions.emitPermissions([.CAMERA: .DISABLED])

        store.configure(with: "0341")

        #expect(store.steps.contains(.addInfo))
        #expect(store.steps.contains(.permissions))
        #expect(store.currentStep == .intro)
    }

    @Test("configure for weight-only scale removes addInfo step")
    func configureWeightOnlyScaleRemovesAddInfo() {
        let (store, _, permissions, _, _, _) = makeSUT()
        permissions.emitPermissions([.CAMERA: .DISABLED])

        store.configure(with: "0342")

        #expect(store.steps.contains(.addInfo) == false)
        #expect(store.steps.contains(.permissions))
    }

    @Test("configure skips permissions step when camera is enabled")
    func configureSkipsPermissionsWhenCameraEnabled() {
        let (store, _, permissions, _, _, _) = makeSUT()
        permissions.emitPermissions([.CAMERA: .ENABLED])

        store.configure(with: "0341")

        #expect(store.steps.contains(.permissions) == false)
        #expect(store.currentStep == .intro)
    }

    @Test("moveToNextStep skips permissions when camera becomes enabled")
    func moveNextSkipsPermissionsWhenEnabled() {
        let (store, _, permissions, _, _, _) = makeSUT()
        permissions.emitPermissions([.CAMERA: .DISABLED])
        store.configure(with: "0341")
        permissions.emitPermissions([.CAMERA: .ENABLED])

        store.moveToNextStep()

        #expect(store.currentStep == .activateScale)
    }

    @Test("moveToPreviousStep skips permissions when camera is enabled")
    func movePreviousSkipsPermissionsWhenEnabled() {
        let (store, _, permissions, _, _, _) = makeSUT()
        permissions.emitPermissions([.CAMERA: .DISABLED])
        store.configure(with: "0341")
        guard let activateIndex = store.steps.firstIndex(of: .activateScale) else {
            Issue.record("Expected activateScale in configured steps")
            return
        }
        store.currentStepIndex = activateIndex
        permissions.emitPermissions([.CAMERA: .ENABLED])

        store.moveToPreviousStep()

        #expect(store.currentStep == .intro)
    }

    @Test("permissions step disables next and triggers permission handling when camera disabled")
    func permissionsStepDisablesNextAndRequestsCameraPermission() async {
        let (store, _, permissions, _, _, _) = makeSUT()
        permissions.emitPermissions([.CAMERA: .DISABLED])
        store.configure(with: "0341")

        guard let permissionsIndex = store.steps.firstIndex(of: .permissions) else {
            Issue.record("Expected permissions step in configured flow")
            return
        }

        store.currentStepIndex = permissionsIndex
        let requested = await waitUntil { permissions.handlePermissionCalls.contains(.camera) }

        #expect(store.isNextEnabled == false)
        #expect(requested == true)
    }

    @Test("permission revocation while on appSync navigates back to permissions")
    func permissionRevokedOnAppSyncReturnsToPermissions() async {
        let (store, _, permissions, _, _, _) = makeSUT()
        permissions.emitPermissions([.CAMERA: .DISABLED])
        store.configure(with: "0341")

        guard let appSyncIndex = store.steps.firstIndex(of: .appSync) else {
            Issue.record("Expected appSync step in configured flow")
            return
        }
        store.currentStepIndex = appSyncIndex
        permissions.emitPermissions([.CAMERA: .DISABLED])

        let moved = await waitUntil { store.currentStep == .permissions }
        #expect(moved == true)
    }


    @Test("completion without active account does not save")
    func completionMissingActiveAccountDoesNotSave() async {
        let (store, account, permissions, notification, scale, _) = makeSUT()
        account.activeAccount = nil
        permissions.emitPermissions([.CAMERA: .ENABLED])
        store.configure(with: "0341")
        store.currentStepIndex = store.steps.count - 1

        store.moveToNextStep()
        _ = await waitUntil { notification.dismissLoaderCalls == 1 }

        #expect(scale.createDeviceCalls == 0)
        #expect(scale.syncAllScalesWithRemoteCalls == 0)
    }

    @Test("completion without setup data exits gracefully")
    func completionMissingSetupDataDoesNotSave() async {
        let (store, account, _, notification, scale, _) = makeSUT()
        account.activeAccount = AppSyncSetupStoreTestFixtures.makeActiveAccount(id: "missing-setup-data")
        store.currentStepIndex = store.steps.count - 1

        store.moveToNextStep()
        _ = await waitUntil { notification.dismissLoaderCalls == 1 }

        #expect(scale.createDeviceCalls == 0)
        #expect(notification.showToastCalls == 0)
    }

    @Test("handleExit shows confirmation alert and primary action triggers dismiss")
    func handleExitShowsAlertAndDismissesOnPrimaryAction() {
        let (store, _, _, notification, _, _) = makeSUT()
        var didDismiss = false
        store.dismissAction = { didDismiss = true }

        store.handleExit()

        #expect(notification.showAlertCalls == 1)
        #expect(notification.alertData?.buttons.count == 2)
        notification.alertData?.buttons.first?.action(nil)
        #expect(didDismiss == true)
    }

    @Test("cleanUp clears setup-in-progress state")
    func cleanupClearsSetupInProgress() {
        let (store, _, permissions, _, _, bluetooth) = makeSUT()
        permissions.emitPermissions([.CAMERA: .ENABLED])
        store.configure(with: "0341")

        #expect(bluetooth.isSetupInProgress == true)
        store.cleanUp()
        #expect(bluetooth.isSetupInProgress == false)
    }

    // MARK: - saveScale success path

    @Test("successful save creates device, syncs, clears flag and dismisses")
    func successfulSaveCreatesDeviceAndDismisses() async {
        let (store, account, permissions, notification, scale, bluetooth) = makeSUT()
        account.activeAccount = AppSyncSetupStoreTestFixtures.makeActiveAccount(id: "save-ok")
        permissions.emitPermissions([.CAMERA: .ENABLED])
        store.configure(with: "0341")

        var didDismiss = false
        store.dismissAction = { didDismiss = true }
        store.currentStepIndex = store.steps.count - 1

        store.moveToNextStep()
        _ = await waitUntil { didDismiss }

        #expect(scale.createDeviceCalls == 1)
        #expect(scale.syncAllScalesWithRemoteCalls == 1)
        #expect(bluetooth.isSetupInProgress == false)
        #expect(didDismiss == true)
        #expect(notification.showLoaderCalls == 1)
    }

    // MARK: - saveScale duplicate removal

    @Test("save removes existing device with same SKU before creating new one")
    func saveDuplicateRemovesExistingDevice() async {
        let (store, account, permissions, _, scale, _) = makeSUT()
        account.activeAccount = AppSyncSetupStoreTestFixtures.makeActiveAccount(id: "dup-remove")
        permissions.emitPermissions([.CAMERA: .ENABLED])

        let existingDevice = Device(
            id: "old-device",
            accountId: "dup-remove",
            sku: "0341",
            deviceName: "Old Scale",
            deviceType: DeviceType.scale.rawValue,
            bathScale: BathScale(scaleType: DeviceSourceType.appsync.rawValue, bodyComp: true)
        )
        scale.scales = [existingDevice.toSnapshot()]

        store.configure(with: "0341")
        var didDismiss = false
        store.dismissAction = { didDismiss = true }
        store.currentStepIndex = store.steps.count - 1

        store.moveToNextStep()
        _ = await waitUntil { didDismiss }

        #expect(scale.deleteDeviceCalls == 1)
        #expect(scale.createDeviceCalls == 1)
    }

    @Test("save continues even when duplicate deletion fails")
    func saveContinuesWhenDuplicateDeletionFails() async {
        let (store, account, permissions, _, scale, _) = makeSUT()
        account.activeAccount = AppSyncSetupStoreTestFixtures.makeActiveAccount(id: "dup-err")
        permissions.emitPermissions([.CAMERA: .ENABLED])

        let existingDevice = Device(
            id: "old-err-device",
            accountId: "dup-err",
            sku: "0341",
            deviceName: "Old Scale",
            deviceType: DeviceType.scale.rawValue,
            bathScale: BathScale(scaleType: DeviceSourceType.appsync.rawValue, bodyComp: true)
        )
        scale.scales = [existingDevice.toSnapshot()]
        scale.deleteDeviceError = AppSyncSetupStoreTestError.saveFailed

        store.configure(with: "0341")
        var didDismiss = false
        store.dismissAction = { didDismiss = true }
        store.currentStepIndex = store.steps.count - 1

        store.moveToNextStep()
        _ = await waitUntil { didDismiss }

        #expect(scale.deleteDeviceCalls == 1)
        #expect(scale.createDeviceCalls == 1)
    }

    @Test("save continues even when getDevices fails")
    func saveContinuesWhenGetDevicesFails() async {
        let (store, account, permissions, _, scale, _) = makeSUT()
        account.activeAccount = AppSyncSetupStoreTestFixtures.makeActiveAccount(id: "get-err")
        permissions.emitPermissions([.CAMERA: .ENABLED])
        scale.getDevicesError = AppSyncSetupStoreTestError.saveFailed

        store.configure(with: "0341")
        var didDismiss = false
        store.dismissAction = { didDismiss = true }
        store.currentStepIndex = store.steps.count - 1

        store.moveToNextStep()
        _ = await waitUntil { didDismiss }

        #expect(scale.createDeviceCalls == 1)
    }

    // MARK: - saveScale createDevice failure

    @Test("createDevice failure shows toast and clears setup flag")
    func createDeviceFailureShowsToast() async {
        let (store, account, permissions, notification, scale, bluetooth) = makeSUT()
        account.activeAccount = AppSyncSetupStoreTestFixtures.makeActiveAccount(id: "create-err")
        permissions.emitPermissions([.CAMERA: .ENABLED])
        scale.createDeviceError = AppSyncSetupStoreTestError.saveFailed

        store.configure(with: "0341")
        store.dismissAction = { }
        store.currentStepIndex = store.steps.count - 1

        store.moveToNextStep()
        _ = await waitUntil { notification.showToastCalls == 1 }

        #expect(notification.showToastCalls == 1)
        #expect(bluetooth.isSetupInProgress == false)
        #expect(scale.createDeviceCalls == 1)
    }

    // MARK: - showHelpModal

    @Test("showHelpModal presents modal via notification service")
    func showHelpModalPresentsModal() {
        let (store, _, permissions, notification, _, _) = makeSUT()
        permissions.emitPermissions([.CAMERA: .ENABLED])
        store.configure(with: "0341")

        store.showHelpModal()

        #expect(notification.showModalCalls == 1)
    }

    // MARK: - stepViews

    @Test("stepViews count matches steps when configured")
    func stepViewsCountMatchesSteps() {
        let (store, _, permissions, _, _, _) = makeSUT()
        permissions.emitPermissions([.CAMERA: .ENABLED])
        store.configure(with: "0341")

        #expect(store.stepViews.count == store.steps.count)
    }

    @Test("stepViews returns empty when not configured")
    func stepViewsEmptyWithoutConfigure() {
        let (store, _, _, _, _, _) = makeSUT()

        #expect(store.stepViews.isEmpty)
    }

    // MARK: - moveToPreviousStep edge cases

    @Test("moveToPreviousStep from intro is no-op")
    func movePreviousFromIntroIsNoOp() {
        let (store, _, permissions, _, _, _) = makeSUT()
        permissions.emitPermissions([.CAMERA: .ENABLED])
        store.configure(with: "0341")

        store.moveToPreviousStep()

        #expect(store.currentStep == .intro)
    }

    // MARK: - permission change on non-appSync step

    @Test("permission revocation on non-appSync step does not navigate")
    func permissionRevokedOnNonAppSyncDoesNotNavigate() async {
        let (store, _, permissions, _, _, _) = makeSUT()
        permissions.emitPermissions([.CAMERA: .DISABLED])
        store.configure(with: "0341")

        store.currentStepIndex = 0
        permissions.emitPermissions([.CAMERA: .DISABLED])

        try? await Task.sleep(nanoseconds: 50_000_000)
        #expect(store.currentStep == .intro)
    }

    // MARK: - isNextEnabled for non-permissions steps

    @Test("isNextEnabled is true for non-permissions steps")
    func isNextEnabledTrueForNonPermissionsSteps() {
        let (store, _, permissions, _, _, _) = makeSUT()
        permissions.emitPermissions([.CAMERA: .ENABLED])
        store.configure(with: "0341")

        #expect(store.isNextEnabled == true)
        store.currentStepIndex = store.steps.count - 1
        #expect(store.isNextEnabled == true)
    }

    // MARK: - configure sets isSetupInProgress

    @Test("configure sets bluetooth isSetupInProgress to true")
    func configureSetsSetupInProgress() {
        let (store, _, permissions, _, _, bluetooth) = makeSUT()
        permissions.emitPermissions([.CAMERA: .ENABLED])
        #expect(bluetooth.isSetupInProgress == false)

        store.configure(with: "0341")

        #expect(bluetooth.isSetupInProgress == true)
    }

    // MARK: - moveToNextStep normal navigation

    @Test("moveToNextStep navigates through all steps in sequence")
    func moveNextNavigatesThroughSteps() {
        let (store, _, permissions, _, _, _) = makeSUT()
        permissions.emitPermissions([.CAMERA: .ENABLED])
        store.configure(with: "0341")

        let expectedSteps = store.steps
        for i in 1 ..< expectedSteps.count {
            store.moveToNextStep()
            #expect(store.currentStep == expectedSteps[i])
        }
    }

    // MARK: - save does not match different SKU duplicates

    @Test("save does not delete device with different SKU")
    func saveDoesNotDeleteDifferentSkuDevice() async {
        let (store, account, permissions, _, scale, _) = makeSUT()
        account.activeAccount = AppSyncSetupStoreTestFixtures.makeActiveAccount(id: "no-dup")
        permissions.emitPermissions([.CAMERA: .ENABLED])

        let differentDevice = Device(
            id: "different-device",
            accountId: "no-dup",
            sku: "9999",
            deviceName: "Other Scale",
            deviceType: DeviceType.scale.rawValue,
            bathScale: BathScale(scaleType: DeviceSourceType.appsync.rawValue, bodyComp: false)
        )
        scale.scales = [differentDevice.toSnapshot()]

        store.configure(with: "0341")
        var didDismiss = false
        store.dismissAction = { didDismiss = true }
        store.currentStepIndex = store.steps.count - 1

        store.moveToNextStep()
        _ = await waitUntil { didDismiss }

        #expect(scale.deleteDeviceCalls == 0)
        #expect(scale.createDeviceCalls == 1)
    }
}

private enum AppSyncSetupStoreTestError: Error {
    case saveFailed
}

@MainActor
// swiftlint:disable:next large_tuple
private func makeSUT() -> (
    store: AppSyncSetupStore,
    account: MockAccountService,
    permissions: MockPermissionsService,
    notification: MockNotificationHelperService,
    scale: MockScaleService,
    bluetooth: MockBluetoothService
) {
    let account = MockAccountService()
    let permissions = MockPermissionsService()
    let notification = MockNotificationHelperService()
    let scale = MockScaleService()
    let bluetooth = MockBluetoothService()
    let logger = MockLoggerService()

    TestDependencyContainer.reset()
    DependencyContainer.shared.register(account as AccountServiceProtocol)
    DependencyContainer.shared.register(permissions as PermissionsServiceProtocol)
    DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
    DependencyContainer.shared.register(scale as PairedDeviceServiceProtocol)
    DependencyContainer.shared.register(bluetooth as BluetoothServiceProtocol)
    DependencyContainer.shared.register(logger as LoggerServiceProtocol)

    let store = AppSyncSetupStore()
    store.notificationService = notification
    store.logger = logger
    store.deviceService = scale
    store.accountService = account
    store.permissionsService = permissions
    store.bluetoothService = bluetooth
    return (store, account, permissions, notification, scale, bluetooth)
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
