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
}

private enum AppSyncSetupStoreTestError: Error {
    case saveFailed
}

@MainActor
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
    DependencyContainer.shared.register(scale as ScaleServiceProtocol)
    DependencyContainer.shared.register(bluetooth as BluetoothServiceProtocol)
    DependencyContainer.shared.register(logger as LoggerServiceProtocol)

    let store = AppSyncSetupStore()
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
