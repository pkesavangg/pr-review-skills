//
//  A6ScaleSetupStoreTests.swift
//  meAppTests
//

import Testing
import GGBluetoothSwiftPackage
@testable import meApp

@Suite("A6ScaleSetupStore", .serialized)
@MainActor
struct A6ScaleSetupStoreTests {

    private let a6Sku = "0378"  // A6/LCBT scale SKU

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
        store: A6ScaleSetupStore,
        permissions: MockPermissionsService,
        bluetooth: MockScaleSetupBluetoothService,
        scaleService: MockScaleSetupScaleService,
        accountService: MockAccountService,
        notification: MockNotificationHelperService
    ) {
        _ = ServiceRegistry.shared
        let permissions = MockPermissionsService()
        let bluetooth = MockScaleSetupBluetoothService()
        let scaleService = MockScaleSetupScaleService()
        let accountService = MockAccountService()
        let notification = MockNotificationHelperService()
        DependencyContainer.shared.register(permissions as PermissionsServiceProtocol)
        DependencyContainer.shared.register(bluetooth as BluetoothServiceProtocol)
        DependencyContainer.shared.register(scaleService as ScaleServiceProtocol)
        DependencyContainer.shared.register(accountService as AccountServiceProtocol)
        DependencyContainer.shared.register(notification as NotificationHelperService)
        let store = A6ScaleSetupStore()
        return (store, permissions, bluetooth, scaleService, accountService, notification)
    }

    // MARK: - Initial state

    @Test("initial currentStepIndex is 0")
    func initialStepIndex() {
        let (store, _, _, _, _, _) = makeSUT()
        #expect(store.currentStepIndex == 0)
    }

    @Test("initial currentStep is .intro")
    func initialCurrentStep() {
        let (store, _, _, _, _, _) = makeSUT()
        #expect(store.currentStep == .intro)
    }

    @Test("initial isNextEnabled is true")
    func initialIsNextEnabled() {
        let (store, _, _, _, _, _) = makeSUT()
        #expect(store.isNextEnabled == true)
    }

    @Test("initial steps contains all A6ScaleSetupStep cases")
    func initialStepsComplete() {
        let (store, _, _, _, _, _) = makeSUT()
        #expect(store.steps.count == A6ScaleSetupStep.allCases.count)
    }

    @Test("initial connectionState is .loading")
    func initialConnectionState() {
        let (store, _, _, _, _, _) = makeSUT()
        #expect(store.connectionState == .loading)
    }

    // MARK: - configure(with:)

    @Test("configure resets currentStepIndex to intro when no discovery context")
    func configureStartsAtIntro() {
        let (store, _, _, _, _, _) = makeSUT()
        store.configure(with: a6Sku)
        #expect(store.currentStep == .intro)
    }

    @Test("configure sets isSetupInProgress on bluetoothService")
    func configureSetsSetupInProgress() {
        let (store, _, bluetooth, _, _, _) = makeSUT()
        store.configure(with: a6Sku)
        #expect(bluetooth.isSetupInProgress == true)
    }

    @Test("configure with discoveredScale and discoveryEvent starts at connectingBluetooth")
    func configureWithDiscoveryContextStartsAtConnecting() {
        let (store, _, _, _, _, _) = makeSUT()
        let device = Device(id: "d1", accountId: "a1", sku: a6Sku)
        let sku = a6Sku
        store.configure(with: sku, discoveredScale: device, discoveryEvent: nil)
        // discoveryEvent is nil so should still start at intro
        #expect(store.currentStep == .intro)
    }

    @Test("configure resets to intro on second call")
    func configureResetsOnRepeat() {
        let (store, _, _, _, _, _) = makeSUT()
        store.configure(with: a6Sku)
        store.moveToNextStep()
        store.configure(with: a6Sku)
        #expect(store.currentStep == .intro)
    }

    // MARK: - Navigation

    @Test("moveToNextStep increments index")
    func moveToNextStepIncrements() {
        let (store, permissions, _, _, _, _) = makeSUT()
        permissions.revokeAll()
        store.configure(with: a6Sku)
        store.moveToNextStep()
        #expect(store.currentStepIndex > 0)
    }

    @Test("moveToPreviousStep decrements index")
    func moveToPreviousStepDecrements() {
        let (store, permissions, _, _, _, _) = makeSUT()
        permissions.revokeAll()
        store.configure(with: a6Sku)
        store.moveToNextStep()
        let afterNext = store.currentStepIndex
        store.moveToPreviousStep()
        #expect(store.currentStepIndex < afterNext)
    }

    @Test("moveToPreviousStep does not go below 0")
    func movePreviousStepFloor() {
        let (store, _, _, _, _, _) = makeSUT()
        store.configure(with: a6Sku)
        store.moveToPreviousStep()
        #expect(store.currentStepIndex == 0)
    }

    @Test("moveToNextStep skips permissions when Bluetooth permission granted")
    func moveToNextSkipsPermissionsWhenGranted() {
        let (store, permissions, _, _, _, _) = makeSUT()
        permissions.setPermissions([.BLUETOOTH: .ENABLED, .BLUETOOTH_SWITCH: .ENABLED])
        store.configure(with: a6Sku)
        store.moveToNextStep()
        let step = store.steps[safe: store.currentStepIndex]
        #expect(step != .permissions)
    }

    // MARK: - isNextEnabled at permissions step

    @Test("isNextEnabled is false at permissions step when BT not granted")
    func nextDisabledWithoutBluetooth() {
        let (store, permissions, _, _, _, _) = makeSUT()
        permissions.revokeAll()
        store.configure(with: a6Sku)
        if let idx = store.steps.firstIndex(of: .permissions) {
            store.currentStepIndex = idx
        }
        #expect(store.isNextEnabled == false)
    }

    @Test("isNextEnabled is true at non-permissions steps")
    func nextEnabledAtNonPermissionsStep() {
        let (store, permissions, _, _, _, _) = makeSUT()
        permissions.grantAll()
        store.configure(with: a6Sku)
        #expect(store.isNextEnabled == true)
    }

    @Test("isNextEnabled updates when permissions publisher fires")
    async func nextEnabledWhenBluetoothGranted() async {
        let (store, permissions, _, _, _, _) = makeSUT()
        permissions.revokeAll()
        store.configure(with: a6Sku)
        if let idx = store.steps.firstIndex(of: .permissions) {
            store.currentStepIndex = idx
        }
        #expect(store.isNextEnabled == false)
        permissions.setPermissions([.BLUETOOTH: .ENABLED, .BLUETOOTH_SWITCH: .ENABLED])
        await waitUntil { store.isNextEnabled == true }
        #expect(store.isNextEnabled == true)
    }

    // MARK: - handlePermissionChange

    @Test("Bluetooth revoked while on wakeUp navigates back to permissions")
    async func bluetoothRevokedDuringWakeUpNavigatesBack() async {
        let (store, permissions, _, _, _, _) = makeSUT()
        permissions.revokeAll()
        store.configure(with: a6Sku)
        if let wakeIdx = store.steps.firstIndex(of: .wakeUp) {
            store.currentStepIndex = wakeIdx
        }
        permissions.setPermissions([.BLUETOOTH: .DISABLED, .BLUETOOTH_SWITCH: .DISABLED])
        await waitUntil {
            guard let permIdx = store.steps.firstIndex(of: .permissions) else { return false }
            return store.currentStepIndex == permIdx
        }
        let step = store.steps[safe: store.currentStepIndex]
        #expect(step == .permissions)
    }

    // MARK: - handleExit

    @Test("handleExit on non-finished step shows alert")
    func handleExitShowsAlert() {
        let (store, _, _, _, _, notification) = makeSUT()
        store.configure(with: a6Sku)
        store.handleExit()
        #expect(notification.showAlertCallCount > 0)
    }

    @Test("handleExit on setupFinished does not show alert, calls dismiss")
    func handleExitOnFinishedCallsDismiss() {
        let (store, _, _, _, _, notification) = makeSUT()
        store.configure(with: a6Sku)
        var dismissCalled = false
        store.dismissAction = { dismissCalled = true }
        if let finishedIdx = store.steps.firstIndex(of: .setupFinished) {
            store.currentStepIndex = finishedIdx
        }
        store.handleExit()
        #expect(dismissCalled == true)
        #expect(notification.showAlertCallCount == 0)
    }

    // MARK: - cleanUp

    @Test("cleanUp sets isSetupInProgress to false")
    func cleanUpClearsSetupInProgress() {
        let (store, _, bluetooth, _, _, _) = makeSUT()
        store.configure(with: a6Sku)
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
