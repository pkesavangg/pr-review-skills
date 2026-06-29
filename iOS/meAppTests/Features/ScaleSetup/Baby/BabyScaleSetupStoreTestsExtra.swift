//
//  BabyScaleSetupStoreTestsExtra.swift
//  meAppTests
//

import Foundation
@testable import meApp
import Testing

@MainActor
extension BabyScaleSetupStoreTests {

    // MARK: - Skip Edit Baby Dialog

    @Test("showSkipBabyProfileDialog with editingBaby sets showSkipEditDialog")
    func showSkipBabyProfileDialog_withEditingBaby_setsEditFlag() {
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.editingBaby = Baby(accountId: "acct-1", name: "Aria")

        store.showSkipBabyProfileDialog()

        #expect(store.showSkipEditDialog == true)
        #expect(store.showSkipDialog == false)
    }

    @Test("showSkipBabyProfileDialog without editingBaby sets showSkipDialog")
    func showSkipBabyProfileDialog_noEditingBaby_setsAddFlag() {
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.editingBaby = nil

        store.showSkipBabyProfileDialog()

        #expect(store.showSkipDialog == true)
        #expect(store.showSkipEditDialog == false)
    }

    @Test("handleSkipEditConfirmed clears dialog, editingBaby, resets form, and navigates to babyAdded")
    func handleSkipEditConfirmed_clearsAndNavigates() {
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.editingBaby = Baby(accountId: "acct-1", name: "Aria")
        store.babyProfileForm.name.value = "Aria"
        store.showSkipEditDialog = true

        store.handleSkipEditConfirmed()

        #expect(store.showSkipEditDialog == false)
        #expect(store.editingBaby == nil)
        #expect(store.babyProfileForm.name.value.isEmpty)
        #expect(store.currentStep == .babyAdded)
    }

    @Test("handleSkipEditCancelled dismisses skip-edit dialog only")
    func handleSkipEditCancelled_dismissesOnly() {
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.showSkipEditDialog = true

        store.handleSkipEditCancelled()

        #expect(store.showSkipEditDialog == false)
    }

    // MARK: - confirmDeleteBabyFromList (MA-3617)

    @Test("confirmDeleteBabyFromList shows delete confirmation alert")
    func confirmDeleteBabyFromList_showsAlert() {
        let (store, notification, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        let baby = Baby(accountId: "acct-1", name: "Delete Me")

        store.confirmDeleteBabyFromList(baby)

        #expect(notification.showAlertCalls == 1)
    }

    // MARK: - handleFinish (MA-3617)

    @Test("handleFinish when scale already saved calls performExitCleanup directly")
    func handleFinish_scaleSaved_exitsDirectly() {
        var dismissed = false
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.isScaleSaved = true
        store.dismissAction = { dismissed = true }

        store.performExitCleanup()

        #expect(dismissed == true)
    }

    @Test("handleFinish when no discovered scale calls performExitCleanup directly")
    func handleFinish_noDiscoveredScale_exitsDirectly() {
        var dismissed = false
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.discoveredScale = nil
        store.discoveryEvent = nil
        store.dismissAction = { dismissed = true }

        store.performExitCleanup()

        #expect(dismissed == true)
    }

    // MARK: - editBaby with nil optional fields (MA-3617)

    @Test("editBaby with nil optional fields sets empty strings for optional form fields")
    func editBaby_nilOptionalFields_setsEmptyStrings() {
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        let baby = Baby(accountId: "acct-1", name: "Simple Baby")
        store.savedBabies = [baby]

        store.editBaby(baby)

        #expect(store.editingBaby?.id == baby.id)
        #expect(store.babyProfileForm.name.value == "Simple Baby")
        #expect(store.babyProfileForm.biologicalSex.value.isEmpty)
        #expect(store.babyProfileForm.birthLengthInches.value.isEmpty)
        #expect(store.babyProfileForm.birthWeightLbs.value.isEmpty)
        #expect(store.babyProfileForm.birthWeightOz.value.isEmpty)
        #expect(store.currentStep == .babyProfile)
    }

    // MARK: - handleStepChange (MA-3627)

    @Test("handleStepChange on wakeup starts bluetooth scan")
    func handleStepChange_wakeup_startsScan() async {
        let (store, _, _, bluetooth, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()

        store.navigateToStep(.wakeup)

        await waitUntilBabyScale { bluetooth.scanForPairingCalls == 1 }
        #expect(bluetooth.scanForPairingCalls == 1)
    }

    @Test("handleStepChange on connectingBluetooth with no discovery sets failure")
    func handleStepChange_connectingBT_noDiscovery_setsFailure() {
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.discoveredScale = nil
        store.discoveryEvent = nil

        store.navigateToStep(.connectingBluetooth)

        #expect(store.connectionState == .loading)
    }

    @Test("stepViews builds a view for every setup step when a scale item is set")
    func stepViewsBuildsViewPerStep() {
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT()

        // No scale item yet → no views.
        #expect(store.stepViews.isEmpty)

        // With a scale item, a view is produced for each configured step.
        store.scaleItem = makeBabyScaleItem()
        let views = store.stepViews
        #expect(views.count == store.steps.count)
        #expect(views.isEmpty == false)
    }

    @Test("handleStepChange does nothing when isExiting")
    func handleStepChange_isExiting_doesNothing() {
        let (store, _, _, bluetooth, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.isExiting = true

        store.currentStepIndex = BabyScaleSetupStep.wakeup.rawValue

        #expect(bluetooth.scanForPairingCalls == 0)
    }

    // MARK: - handleDeviceDiscovery (MA-3627)

    @Test("handleDeviceDiscovery ignores non-babyScale devices")
    func handleDeviceDiscovery_nonBabyScale_ignored() {
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.navigateToStep(.wakeup)

        let device = ScaleTestFixtures.makeDevice(id: "non-baby-1")
        let scaleInfo = DeviceItemInfo(productName: "Regular Scale", sku: "0100", imgPath: "scale0100", setupType: .bluetooth, bodyComp: false)
        let event = DeviceDiscoveryEvent(device: device.toSnapshot(), deviceInfo: scaleInfo, protocolType: .R4, isNew: true)

        store.handleDeviceDiscovery(event)

        #expect(store.discoveredScale == nil)
    }

    @Test("handleDeviceDiscovery ignores events when not on wakeup step")
    func handleDeviceDiscovery_notOnWakeup_ignored() {
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        // Stay on intro step
        let event = makeBabyDiscoveryEvent()

        store.handleDeviceDiscovery(event)

        #expect(store.discoveredScale == nil)
    }

    @Test("handleDeviceDiscovery for new baby scale sets discovered state and starts pairing")
    func handleDeviceDiscovery_newBabyScale_startsPairing() async {
        let bluetooth = MockBluetoothService()
        bluetooth.confirmSmartPairResult = .success(.creationCompleted)
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT(bluetoothService: bluetooth)
        store.scaleItem = makeBabyScaleItem()
        store.navigateToStep(.wakeup)

        let event = makeBabyDiscoveryEvent(isNew: true)
        store.handleDeviceDiscovery(event)

        #expect(store.discoveredScale != nil)
        #expect(store.discoveryEvent != nil)
        #expect(store.connectionState == .loading)
    }

    @Test("handleDeviceDiscovery for known baby scale shows alert")
    func handleDeviceDiscovery_knownScale_showsAlert() {
        let (store, notification, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.navigateToStep(.wakeup)

        let event = makeBabyDiscoveryEvent(isNew: false)
        store.handleDeviceDiscovery(event)

        #expect(store.discoveredScale != nil)
        #expect(notification.showAlertCalls == 1)
        #expect(notification.alertData?.title == "Scale Already Paired")
    }

    // MARK: - confirmPair (MA-3627)

    @Test("confirmPair success: saves scale, sets connectionState to success, navigates to scaleName")
    func confirmPair_success_savesAndNavigates() async {
        let bluetooth = MockBluetoothService()
        bluetooth.confirmSmartPairResult = .success(.creationCompleted)
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", email: "test@test.com", firstName: "Tester", isActiveAccount: true)
        let (store, _, _, _, _, scale, _) = makeBabyScaleSUT(bluetoothService: bluetooth, accountService: account)
        store.scaleItem = makeBabyScaleItem()
        let device = ScaleTestFixtures.makeDevice(id: "baby-scale-1")
        store.discoveredScale = device.toSnapshot()
        store.discoveryEvent = makeBabyDiscoveryEvent(device: device)

        await store.confirmPair()

        #expect(store.connectionState == .success)
        #expect(store.scaleSetupError == .none)
        #expect(store.currentStep == .scaleName)
        #expect(scale.createR4ScaleCalls == 1)
    }

    @Test("confirmPair failure response: sets failure state and navigates to connectingBluetooth")
    func confirmPair_failureResponse_setsFailure() async {
        let bluetooth = MockBluetoothService()
        bluetooth.confirmSmartPairResult = .success(.creationFailed)
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT(bluetoothService: bluetooth)
        store.scaleItem = makeBabyScaleItem()
        let device = ScaleTestFixtures.makeDevice(id: "baby-scale-1")
        store.discoveredScale = device.toSnapshot()
        store.discoveryEvent = makeBabyDiscoveryEvent(device: device)

        await store.confirmPair()

        #expect(store.connectionState == .failure)
        #expect(store.scaleSetupError == .pairingFailed)
        #expect(store.currentStep == .connectionError)
    }

    @Test("confirmPair error: sets connectionFailed and navigates to connectingBluetooth")
    func confirmPair_error_setsConnectionFailed() async {
        let bluetooth = MockBluetoothService()
        bluetooth.confirmSmartPairResult = .failure(.confirmPairFailed(BabyScaleSetupTestError.genericFailure))
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT(bluetoothService: bluetooth)
        store.scaleItem = makeBabyScaleItem()
        let device = ScaleTestFixtures.makeDevice(id: "baby-scale-1")
        store.discoveredScale = device.toSnapshot()
        store.discoveryEvent = makeBabyDiscoveryEvent(device: device)

        await store.confirmPair()

        #expect(store.connectionState == .failure)
        #expect(store.scaleSetupError == .connectionFailed)
        #expect(store.currentStep == .connectionError)
    }

    @Test("confirmPair with missing discovery data: sets failure")
    func confirmPair_missingData_setsFailure() async {
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.discoveredScale = nil
        store.discoveryEvent = nil

        await store.confirmPair()

        #expect(store.connectionState == .failure)
    }

    // MARK: - resetDiscoveryState (MA-3627)

    @Test("resetDiscoveryState clears all discovery-related state")
    func resetDiscoveryState_clearsState() {
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.discoveredScale = ScaleTestFixtures.makeDevice(id: "test-1").toSnapshot()
        store.discoveryEvent = makeBabyDiscoveryEvent()

        store.resetDiscoveryState()

        #expect(store.discoveredScale == nil)
        #expect(store.discoveryEvent == nil)
        #expect(store.deviceDiscoveryCancellable == nil)
        #expect(store.scanTimeoutTask == nil)
    }

    // MARK: - handleNextButtonClick scaleName (MA-3627)

    @Test("handleNextButtonClick on scaleName updates nickname and moves to next step")
    func handleNextButtonClick_scaleName_updatesNicknameAndMoves() async {
        let (store, _, _, _, _, scale, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        let device = Device(id: "scale-1", accountId: "acct-1", deviceType: DeviceType.scale.rawValue, createdAt: "")
        store.savedScale = device.toSnapshot()
        store.navigateToStep(.scaleName)
        store.scaleNicknameForm.nickname.value = "My Baby Scale"

        store.handleNextButtonClick()

        let called = await waitUntilBabyScale { scale.editDeviceCalls == 1 }
        #expect(called == true)
        #expect(store.currentStep == .paired)
    }

    // MARK: - handleExit after scaleName with saved scale (MA-3627)

    @Test("handleExit after scaleName with saved scale exits without alert")
    func handleExit_afterScaleName_savedScale_exitsDirectly() {
        var dismissed = false
        let (store, notification, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.isScaleSaved = true
        store.dismissAction = { dismissed = true }
        store.navigateToStep(.scaleName)

        store.handleExit()

        #expect(dismissed == true)
        #expect(notification.showAlertCalls == 0)
    }

    // MARK: - Back button on babyAdded (MA-3627)

    @Test("handleBackButtonClick on babyAdded navigates to babyProfile")
    func handleBackButtonClick_babyAdded_navigatesToBabyProfile() {
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.navigateToStep(.babyAdded)

        store.handleBackButtonClick()

        #expect(store.currentStep == .babyProfile)
    }

    // MARK: - handleNextButtonClick on paired (MA-3627)

    @Test("handleNextButtonClick on paired moves to babyProfile")
    func handleNextButtonClick_paired_movesToBabyProfile() {
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.navigateToStep(.paired)

        store.handleNextButtonClick()

        #expect(store.currentStep == .babyProfile)
    }

    // MARK: - startBluetoothScan (MA-3627)

    @Test("startBluetoothScan resets discovery state and calls scanForPairing")
    func startBluetoothScan_resetsAndScans() async {
        let (store, _, _, bluetooth, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.discoveredScale = ScaleTestFixtures.makeDevice(id: "old-1").toSnapshot()

        store.startBluetoothScan()

        #expect(store.discoveredScale == nil)
        await waitUntilBabyScale { bluetooth.scanForPairingCalls == 1 }
        #expect(bluetooth.scanForPairingCalls == 1)
        #expect(store.scanTimeoutTask != nil)
    }

    // MARK: - arePermissionsEnabled

    @Test("arePermissionsEnabled returns true when both BT permissions enabled")
    func arePermissionsEnabled_bothEnabled_returnsTrue() {
        let permissions = MockPermissionsService()
        permissions.permissions = [
            .BLUETOOTH: .ENABLED,
            .BLUETOOTH_SWITCH: .ENABLED
        ]
        let (store, notification, _, bluetooth, account, scale, babyService) = makeBabyScaleSUT(permissionsService: permissions)
        #expect(store.arePermissionsEnabled() == true)
    }

    @Test("arePermissionsEnabled returns false when permissions nil")
    func arePermissionsEnabled_nil_returnsFalse() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        #expect(store.arePermissionsEnabled() == false)
    }

    @Test("arePermissionsEnabled returns false when only one permission enabled")
    func arePermissionsEnabled_oneEnabled_returnsFalse() {
        let permissions = MockPermissionsService()
        permissions.permissions = [
            .BLUETOOTH: .ENABLED,
            .BLUETOOTH_SWITCH: .DISABLED
        ]
        let (store, notification, _, bluetooth, account, scale, babyService) = makeBabyScaleSUT(permissionsService: permissions)
        #expect(store.arePermissionsEnabled() == false)
    }

    // MARK: - showHelpModal

    @Test("showHelpModal presents alert via notification service")
    func showHelpModal_presentsAlert() {
        let sut = makeBabyScaleSUT()
        let store = sut.store
        let notification = sut.notification
        store.scaleItem = makeBabyScaleItem()
        store.showHelpModal()
        #expect(notification.showAlertCalls == 1)
        #expect(notification.alertData?.title == "Need Help?")
    }

    // MARK: - Skip Dialog Flow (MA-3617)

    @Test("showSkipBabyProfileDialog sets showSkipDialog to true")
    func showSkipBabyProfileDialog_setsFlag() {
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()

        store.showSkipBabyProfileDialog()

        #expect(store.showSkipDialog == true)
    }

    @Test("handleSkipConfirmed dismisses dialog and calls handleFinish")
    func handleSkipConfirmed_dismissesAndFinishes() {
        var dismissed = false
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.dismissAction = { dismissed = true }
        store.showSkipDialog = true

        store.handleSkipConfirmed()

        #expect(store.showSkipDialog == false)
        #expect(store.currentStep == .done)
    }

    @Test("handleSkipCancelled dismisses dialog without finishing")
    func handleSkipCancelled_dismissesOnly() {
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.showSkipDialog = true

        store.handleSkipCancelled()

        #expect(store.showSkipDialog == false)
    }

    // MARK: - Cleanup

    @Test("performExitCleanup sets isSetupInProgress to false")
    func performExitCleanup_setsSetupInProgressFalse() {
        let sut = makeBabyScaleSUT()
        let store = sut.store
        let bluetooth = sut.bluetooth
        store.scaleItem = makeBabyScaleItem()
        bluetooth.isSetupInProgress = true

        store.performExitCleanup()

        #expect(bluetooth.isSetupInProgress == false)
    }

    @Test("performExitCleanup calls dismissAction")
    func performExitCleanup_callsDismissAction() {
        var dismissed = false
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.dismissAction = { dismissed = true }

        store.performExitCleanup()

        #expect(dismissed == true)
    }

    @Test("cleanup nils out dismissAction and cancels subscriptions")
    func cleanup_clearsReferences() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.dismissAction = { }

        store.cleanup()

        #expect(store.dismissAction == nil)
        #expect(store.cancellables.isEmpty)
        #expect(store.deviceDiscoveryCancellable == nil)
        #expect(store.scanTimeoutTask == nil)
    }
}

// MARK: - Test Errors

enum BabyScaleSetupTestError: Error {
    case genericFailure
}

// MARK: - makeSUT

// swiftlint:disable large_tuple
@MainActor
func makeBabyScaleSUT(
    notificationService: MockNotificationHelperService? = nil,
    permissionsService: MockPermissionsService? = nil,
    bluetoothService: MockBluetoothService? = nil,
    accountService: MockAccountService? = nil,
    scaleService: MockScaleService? = nil,
    babyService: MockBabyService? = nil
) -> (
    store: BabyScaleSetupStore,
    notification: MockNotificationHelperService,
    permissions: MockPermissionsService,
    bluetooth: MockBluetoothService,
    account: MockAccountService,
    scale: MockScaleService,
    babyService: MockBabyService
) {
    // swiftlint:enable large_tuple
    let notification = notificationService ?? MockNotificationHelperService()
    let permissions = permissionsService ?? MockPermissionsService()
    let bluetooth = bluetoothService ?? MockBluetoothService()
    let account = accountService ?? MockAccountService()
    let scale = scaleService ?? MockScaleService()
    let baby = babyService ?? MockBabyService()

    TestDependencyContainer.reset()
    DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
    DependencyContainer.shared.register(permissions as PermissionsServiceProtocol)
    DependencyContainer.shared.register(bluetooth as BluetoothServiceProtocol)
    DependencyContainer.shared.register(account as AccountServiceProtocol)
    DependencyContainer.shared.register(scale as PairedDeviceServiceProtocol)
    DependencyContainer.shared.register(baby as BabyServiceProtocol)

    let store = BabyScaleSetupStore()
    store.notificationService = notification
    store.permissionsService = permissions
    store.bluetoothService = bluetooth
    store.accountService = account
    store.deviceService = scale
    store.babyService = baby

    return (
        store: store,
        notification: notification,
        permissions: permissions,
        bluetooth: bluetooth,
        account: account,
        scale: scale,
        babyService: baby
    )
}

// MARK: - Helpers

@MainActor
func makeBabyScaleItem() -> DeviceItemInfo {
    guard let scale = SCALES.first(where: { $0.sku == "0220" }) ?? SCALES.first ?? BPMS.first else {
        fatalError("No scale items available for testing")
    }
    return scale
}

@MainActor
func makeBabyDiscoveryEvent(device: Device? = nil, isNew: Bool = true) -> DeviceDiscoveryEvent {
    let dev = device ?? ScaleTestFixtures.makeDevice(id: "baby-scale-1")
    let scaleInfo = DeviceItemInfo(productName: "Smart Baby Scale", sku: "0220", imgPath: "scale0220", setupType: .babyScale, bodyComp: false)
    return DeviceDiscoveryEvent(device: dev.toSnapshot(), deviceInfo: scaleInfo, protocolType: .R4, isNew: isNew)
}

@MainActor
func waitUntilBabyScale(
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
