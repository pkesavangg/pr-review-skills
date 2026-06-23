//
//  AppSyncSetupStoreTests.swift
//  meAppTests
//

import Testing
import GGBluetoothSwiftPackage
@testable import meApp

@Suite("AppSyncSetupStore", .serialized)
@MainActor
struct AppSyncSetupStoreTests {

    // SKU for a bodyComp scale (addInfo step included) and a non-bodyComp scale
    private let bodyCompSku = "0341"       // bodyComp: true
    private let noBodyCompSku = "0342"     // bodyComp: false

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 2_000_000_000,
        pollNanoseconds: UInt64 = 10_000_000,
        condition: @MainActor () -> Bool
    ) async {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while ContinuousClock.now < deadline {
            if condition() { return }
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
    }

    @MainActor
    private func makeSUT() -> (
        store: AppSyncSetupStore,
        permissions: MockPermissionsService,
        scaleService: MockScaleSetupScaleService,
        accountService: MockAccountService,
        bluetooth: MockScaleSetupBluetoothService,
        logger: MockLoggerService,
        notification: MockNotificationHelperService
    ) {
        _ = ServiceRegistry.shared
        let permissions = MockPermissionsService()
        let scaleService = MockScaleSetupScaleService()
        let accountService = MockAccountService()
        let bluetooth = MockScaleSetupBluetoothService()
        let logger = MockLoggerService()
        let notification = MockNotificationHelperService()
        DependencyContainer.shared.register(permissions as PermissionsServiceProtocol)
        DependencyContainer.shared.register(scaleService as ScaleServiceProtocol)
        DependencyContainer.shared.register(accountService as AccountServiceProtocol)
        DependencyContainer.shared.register(bluetooth as BluetoothServiceProtocol)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)
        DependencyContainer.shared.register(notification as NotificationHelperService)
        let store = AppSyncSetupStore()
        return (store, permissions, scaleService, accountService, bluetooth, logger, notification)
    }

    // MARK: - Initial state

    @Test("initial currentStepIndex is 0")
    func initialStepIndex() {
        let (store, _, _, _, _, _, _) = makeSUT()
        #expect(store.currentStepIndex == 0)
    }

    @Test("initial currentStep is .intro")
    func initialCurrentStep() {
        let (store, _, _, _, _, _, _) = makeSUT()
        #expect(store.currentStep == .intro)
    }

    @Test("initial isNextEnabled is true")
    func initialIsNextEnabled() {
        let (store, _, _, _, _, _, _) = makeSUT()
        #expect(store.isNextEnabled == true)
    }

    @Test("initial steps contains all AppSyncSetupStep cases")
    func initialStepsContainsAllCases() {
        let (store, _, _, _, _, _, _) = makeSUT()
        #expect(store.steps.count == AppSyncSetupStep.allCases.count)
    }

    // MARK: - configure(with:)

    @Test("configure sets up steps for bodyComp scale including addInfo")
    func configureBodyCompScaleIncludesAddInfo() {
        let (store, permissions, _, _, _, _, _) = makeSUT()
        permissions.revokeAll()
        store.configure(with: bodyCompSku)
        #expect(store.steps.contains(.addInfo))
    }

    @Test("configure sets up steps for non-bodyComp scale excluding addInfo")
    func configureNonBodyCompScaleExcludesAddInfo() {
        let (store, permissions, _, _, _, _, _) = makeSUT()
        permissions.revokeAll()
        store.configure(with: noBodyCompSku)
        #expect(!store.steps.contains(.addInfo))
    }

    @Test("configure resets currentStepIndex to 0")
    func configureResetsIndex() {
        let (store, permissions, _, _, _, _, _) = makeSUT()
        permissions.revokeAll()
        store.configure(with: bodyCompSku)
        store.moveToNextStep()
        store.configure(with: bodyCompSku)
        #expect(store.currentStepIndex == 0)
    }

    @Test("configure sets isSetupInProgress on bluetoothService")
    func configureSetsSetupInProgress() {
        let (store, _, _, _, bluetooth, _, _) = makeSUT()
        store.configure(with: bodyCompSku)
        #expect(bluetooth.isSetupInProgress == true)
    }

    @Test("configure with camera permission granted skips permissions step")
    func configureWithCameraGrantedSkipsPermissions() {
        let (store, permissions, _, _, _, _, _) = makeSUT()
        let grantedState: [GGPermissionType: GGPermissionState] = [.CAMERA: .ENABLED]
        permissions.setPermissions(grantedState)
        store.configure(with: bodyCompSku)
        #expect(!store.steps.contains(.permissions))
    }

    @Test("configure with camera permission denied includes permissions step")
    func configureWithCameraDeniedIncludesPermissions() {
        let (store, permissions, _, _, _, _, _) = makeSUT()
        permissions.revokeAll()
        store.configure(with: bodyCompSku)
        #expect(store.steps.contains(.permissions))
    }

    // MARK: - Navigation

    @Test("moveToNextStep increments currentStepIndex by 1")
    func moveToNextStepIncrements() {
        let (store, permissions, _, _, _, _, _) = makeSUT()
        permissions.revokeAll()
        store.configure(with: bodyCompSku)
        let initialIndex = store.currentStepIndex
        store.moveToNextStep()
        #expect(store.currentStepIndex == initialIndex + 1)
    }

    @Test("moveToPreviousStep decrements currentStepIndex by 1")
    func moveToPreviousStepDecrements() {
        let (store, permissions, _, _, _, _, _) = makeSUT()
        permissions.revokeAll()
        store.configure(with: bodyCompSku)
        store.moveToNextStep()
        let indexAfterNext = store.currentStepIndex
        store.moveToPreviousStep()
        #expect(store.currentStepIndex == indexAfterNext - 1)
    }

    @Test("moveToPreviousStep does not go below 0")
    func moveToPreviousStepFloorAtZero() {
        let (store, permissions, _, _, _, _, _) = makeSUT()
        permissions.revokeAll()
        store.configure(with: bodyCompSku)
        store.moveToPreviousStep()
        #expect(store.currentStepIndex == 0)
    }

    @Test("moveToNextStep skips permissions step when camera already granted")
    func moveToNextSkipsPermissionsWhenGranted() {
        let (store, permissions, _, _, _, _, _) = makeSUT()
        permissions.revokeAll()
        store.configure(with: bodyCompSku)
        // Grant camera permission
        permissions.setPermissions([.CAMERA: .ENABLED])
        store.moveToNextStep()
        // Should skip permissions and land on activateScale
        let step = store.steps[store.currentStepIndex]
        #expect(step != .permissions)
    }

    // MARK: - updateNextEnabled at permissions step

    @Test("isNextEnabled is false at permissions step when camera not granted")
    func nextDisabledAtPermissionsWithoutCamera() {
        let (store, permissions, _, _, _, _, _) = makeSUT()
        permissions.revokeAll()
        store.configure(with: bodyCompSku)
        // Navigate to permissions step if not already there
        if let permIdx = store.steps.firstIndex(of: .permissions) {
            store.currentStepIndex = permIdx
        }
        #expect(store.isNextEnabled == false)
    }

    @Test("isNextEnabled updates when permissions publisher fires")
    async func nextEnabledWhenPermissionsGranted() async {
        let (store, permissions, _, _, _, _, _) = makeSUT()
        permissions.revokeAll()
        store.configure(with: bodyCompSku)
        if let permIdx = store.steps.firstIndex(of: .permissions) {
            store.currentStepIndex = permIdx
        }
        #expect(store.isNextEnabled == false)
        permissions.setPermissions([.CAMERA: .ENABLED])
        await waitUntil { store.isNextEnabled == true }
        #expect(store.isNextEnabled == true)
    }

    // MARK: - handlePermissionChange

    @Test("permission revoked during appSync navigates back to permissions")
    async func permissionRevokedDuringAppSyncNavigatesBack() async {
        let (store, permissions, _, _, _, _, _) = makeSUT()
        permissions.revokeAll()
        store.configure(with: bodyCompSku)
        // Navigate to appSync step
        if let appSyncIdx = store.steps.firstIndex(of: .appSync) {
            store.currentStepIndex = appSyncIdx
        }
        // Now revoke camera
        permissions.setPermissions([.CAMERA: .DISABLED])
        await waitUntil {
            guard let permIdx = store.steps.firstIndex(of: .permissions) else { return false }
            return store.currentStepIndex == permIdx
        }
        let currentStep = store.steps[safe: store.currentStepIndex]
        #expect(currentStep == .permissions)
    }

    // MARK: - handleExit

    @Test("handleExit shows alert notification")
    func handleExitShowsAlert() {
        let (store, _, _, _, _, _, notification) = makeSUT()
        store.configure(with: bodyCompSku)
        store.handleExit()
        #expect(notification.showAlertCallCount > 0)
    }

    // MARK: - cleanUp

    @Test("cleanUp sets isSetupInProgress to false")
    func cleanUpClearsSetupInProgress() {
        let (store, _, _, _, bluetooth, _, _) = makeSUT()
        store.configure(with: bodyCompSku)
        #expect(bluetooth.isSetupInProgress == true)
        store.cleanUp()
        #expect(bluetooth.isSetupInProgress == false)
    }
}

private extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
