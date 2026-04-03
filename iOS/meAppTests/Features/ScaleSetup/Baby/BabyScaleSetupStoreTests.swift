//
//  BabyScaleSetupStoreTests.swift
//  meAppTests
//

import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct BabyScaleSetupStoreTests {

    // MARK: - Navigation Flow

    @Test("moveToNextStep advances currentStepIndex and currentStep")
    func moveToNextStep_advancesStep() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        #expect(store.currentStep == .intro)
        store.moveToNextStep()
        #expect(store.currentStep == .permissions)
    }

    @Test("moveToPreviousStep decrements step")
    func moveToPreviousStep_decrementsStep() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.permissions.rawValue
        store.moveToPreviousStep()
        #expect(store.currentStep == .intro)
    }

    @Test("moveToPreviousStep does nothing on intro step")
    func moveToPreviousStep_onIntro_doesNothing() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        #expect(store.currentStep == .intro)
        store.moveToPreviousStep()
        #expect(store.currentStep == .intro)
    }

    @Test("navigateToStep jumps to specific step")
    func navigateToStep_jumpsToStep() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.navigateToStep(.scaleName)
        #expect(store.currentStep == .scaleName)
    }

    @Test("adjustedIndex skips permissions when BT permissions granted going forward")
    func adjustedIndex_skipsPermissions_whenGranted_forward() {
        let permissions = MockPermissionsService()
        permissions.permissions = [
            .BLUETOOTH: .ENABLED,
            .BLUETOOTH_SWITCH: .ENABLED
        ]
        let sut = makeSUT(permissionsService: permissions)
        let store = sut.store
        store.scaleItem = makeScaleItem()
        // From intro (0), next should skip permissions (1) and land on wakeup (2)
        let adjusted = store.adjustedIndex(from: 1, direction: 1)
        #expect(store.steps[adjusted] == .wakeup)
    }

    @Test("adjustedIndex does not skip permissions when BT permissions not granted")
    func adjustedIndex_doesNotSkipPermissions_whenNotGranted() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        let adjusted = store.adjustedIndex(from: 1, direction: 1)
        #expect(store.steps[adjusted] == .permissions)
    }

    @Test("adjustedIndex skips wakeup and connectingBluetooth going backward")
    func adjustedIndex_skipsWakeupAndConnecting_goingBackward() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        // From scaleName (4), going backward should skip connectingBluetooth (3) and wakeup (2)
        let adjusted = store.adjustedIndex(from: 3, direction: -1)
        let step = store.steps[adjusted]
        #expect(step == .permissions || step == .intro)
    }

    @Test("back button on babyProfile with saved babies navigates to babyAdded")
    func handleBackButtonClick_babyProfile_withSavedBabies_navigatesToBabyAdded() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        let baby = Baby(accountId: "acct-1", name: "Test Baby")
        store.savedBabies = [baby]
        store.navigateToStep(.babyProfile)
        store.handleBackButtonClick()
        #expect(store.currentStep == .babyAdded)
    }

    @Test("back button on babyProfile with no saved babies navigates to previous step")
    func handleBackButtonClick_babyProfile_noSavedBabies_movesToPreviousStep() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.navigateToStep(.babyProfile)
        store.handleBackButtonClick()
        #expect(store.currentStep == .paired)
    }

    @Test("back button on babyProfile resets form when no saved babies")
    func handleBackButtonClick_babyProfile_noSavedBabies_resetsForm() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.navigateToStep(.babyProfile)
        store.babyProfileForm.name.value = "Some Name"
        store.handleBackButtonClick()
        #expect(store.babyProfileForm.name.value == "")
    }

    @Test("isBackButtonDisabled returns true on intro step")
    func isBackButtonDisabled_onIntro_returnsTrue() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        #expect(store.isBackButtonDisabled() == true)
    }

    @Test("isBackButtonDisabled returns false on non-intro step")
    func isBackButtonDisabled_onPermissions_returnsFalse() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.permissions.rawValue
        #expect(store.isBackButtonDisabled() == false)
    }

    @Test("shouldShowFooter returns false during wakeup step")
    func shouldShowFooter_wakeup_returnsFalse() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.wakeup.rawValue
        #expect(store.shouldShowFooter() == false)
    }

    @Test("shouldShowFooter returns false during connectingBluetooth step")
    func shouldShowFooter_connectingBluetooth_returnsFalse() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.connectingBluetooth.rawValue
        #expect(store.shouldShowFooter() == false)
    }

    @Test("shouldShowFooter returns true on other steps")
    func shouldShowFooter_intro_returnsTrue() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        #expect(store.shouldShowFooter() == true)
    }

    // MARK: - Permissions & Next Button

    @Test("permissions step: isNextEnabled is false when BT permissions disabled")
    func updateNextEnabled_permissions_disabled_nextDisabled() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.permissions.rawValue
        store.updateNextEnabled()
        #expect(store.isNextEnabled == false)
    }

    @Test("permissions step: isNextEnabled is true when both BT permissions enabled")
    func updateNextEnabled_permissions_enabled_nextEnabled() {
        let permissions = MockPermissionsService()
        permissions.permissions = [
            .BLUETOOTH: .ENABLED,
            .BLUETOOTH_SWITCH: .ENABLED
        ]
        let sut = makeSUT(permissionsService: permissions)
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.permissions.rawValue
        store.updateNextEnabled()
        #expect(store.isNextEnabled == true)
    }

    @Test("permissions step: isNextEnabled false when only BLUETOOTH enabled but BLUETOOTH_SWITCH disabled")
    func updateNextEnabled_permissions_partiallyEnabled_nextDisabled() {
        let permissions = MockPermissionsService()
        permissions.permissions = [
            .BLUETOOTH: .ENABLED,
            .BLUETOOTH_SWITCH: .DISABLED
        ]
        let sut = makeSUT(permissionsService: permissions)
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.permissions.rawValue
        store.updateNextEnabled()
        #expect(store.isNextEnabled == false)
    }

    @Test("intro step: isNextEnabled is always true")
    func updateNextEnabled_intro_alwaysTrue() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.updateNextEnabled()
        #expect(store.isNextEnabled == true)
    }

    @Test("paired step: isNextEnabled is always true")
    func updateNextEnabled_paired_alwaysTrue() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.paired.rawValue
        store.updateNextEnabled()
        #expect(store.isNextEnabled == true)
    }

    @Test("babyAdded step: isNextEnabled is always true")
    func updateNextEnabled_babyAdded_alwaysTrue() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.babyAdded.rawValue
        store.updateNextEnabled()
        #expect(store.isNextEnabled == true)
    }

    @Test("scaleName step: isNextEnabled follows scaleNicknameForm.isValid")
    func updateNextEnabled_scaleName_followsFormValidity() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.scaleName.rawValue
        // Default nickname is "Smart Baby Scale" which is valid
        store.updateNextEnabled()
        #expect(store.isNextEnabled == true)

        // Clear the nickname to make form invalid
        store.scaleNicknameForm.nickname.value = ""
        store.updateNextEnabled()
        #expect(store.isNextEnabled == false)
    }

    @Test("babyProfile step: isNextEnabled follows babyProfileForm.isProfileValid")
    func updateNextEnabled_babyProfile_followsFormValidity() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.babyProfile.rawValue

        // Empty name should be invalid
        store.babyProfileForm.name.value = ""
        store.updateNextEnabled()
        #expect(store.isNextEnabled == false)

        // Valid name should be valid
        store.babyProfileForm.name.value = "Baby Name"
        store.updateNextEnabled()
        #expect(store.isNextEnabled == true)
    }

    // MARK: - Baby Profile Flow

    @Test("saveBabyProfile creates baby and appends to savedBabies")
    func saveBabyProfile_createsAndAppends() async {
        let sut = makeSUT()
        let store = sut.store
        let babyService = sut.babyService
        store.scaleItem = makeScaleItem()
        store.navigateToStep(.babyProfile)
        store.babyProfileForm.name.value = "Test Baby"

        await store.saveBabyProfile()

        #expect(babyService.saveBabyCalls == 1)
        #expect(babyService.lastSavedName == "Test Baby")
        #expect(store.savedBabies.count == 1)
        #expect(store.currentStep == .babyAdded)
    }

    @Test("saveBabyProfile with editingBaby calls updateBabyProfile")
    func saveBabyProfile_editing_callsUpdate() async {
        let sut = makeSUT()
        let store = sut.store
        let babyService = sut.babyService
        store.scaleItem = makeScaleItem()
        let baby = Baby(accountId: "acct-1", name: "Old Name")
        store.savedBabies = [baby]
        store.editingBaby = baby
        store.navigateToStep(.babyProfile)
        store.babyProfileForm.name.value = "New Name"

        await store.saveBabyProfile()

        #expect(babyService.updateBabyProfileCalls == 1)
        #expect(babyService.saveBabyCalls == 0)
        #expect(store.editingBaby == nil)
        #expect(store.currentStep == .babyAdded)
    }

    @Test("saveBabyProfile sets error on failure")
    func saveBabyProfile_failure_setsError() async {
        let babyService = MockBabyService()
        babyService.saveBabyError = BabyScaleSetupTestError.genericFailure
        let sut = makeSUT(babyService: babyService)
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.navigateToStep(.babyProfile)
        store.babyProfileForm.name.value = "Test Baby"

        await store.saveBabyProfile()

        #expect(store.scaleSetupError == .profileSaveFailed)
        #expect(store.savedBabies.isEmpty)
    }

    @Test("saveBabyProfile does nothing when name is empty")
    func saveBabyProfile_emptyName_doesNothing() async {
        let sut = makeSUT()
        let store = sut.store
        let babyService = sut.babyService
        store.scaleItem = makeScaleItem()
        store.navigateToStep(.babyProfile)
        store.babyProfileForm.name.value = ""

        await store.saveBabyProfile()

        #expect(babyService.saveBabyCalls == 0)
    }

    @Test("editBaby populates form fields and navigates to babyProfile")
    func editBaby_populatesFormAndNavigates() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        let baby = Baby(accountId: "acct-1", name: "My Baby",
                        birthday: Date(), biologicalSex: "Male",
                        birthLengthInches: 20.5, birthWeightLbs: 7, birthWeightOz: 4.5)
        store.savedBabies = [baby]

        store.editBaby(baby)

        #expect(store.editingBaby?.id == baby.id)
        #expect(store.babyProfileForm.name.value == "My Baby")
        #expect(store.babyProfileForm.biologicalSex.value == "Male")
        #expect(store.babyProfileForm.birthLengthInches.value == "20.5")
        #expect(store.babyProfileForm.birthWeightLbs.value == "7")
        #expect(store.babyProfileForm.birthWeightOz.value == "4.5")
        #expect(store.currentStep == .babyProfile)
    }

    @Test("addAnotherBaby resets form and navigates to babyProfile")
    func addAnotherBaby_resetsFormAndNavigates() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.navigateToStep(.babyAdded)
        store.babyProfileForm.name.value = "Previous Baby"

        store.addAnotherBaby()

        #expect(store.babyProfileForm.name.value == "")
        #expect(store.currentStep == .babyProfile)
    }

    @Test("deleteBabyFromList removes baby and calls service")
    func deleteBabyFromList_removesAndCallsService() async {
        let sut = makeSUT()
        let store = sut.store
        let babyService = sut.babyService
        store.scaleItem = makeScaleItem()
        let baby = Baby(accountId: "acct-1", name: "Test Baby")
        store.savedBabies = [baby]
        store.navigateToStep(.babyAdded)

        store.deleteBabyFromList(baby)

        #expect(store.savedBabies.isEmpty)
        let deleted = await waitUntil { babyService.deleteBabyCalls == 1 }
        #expect(deleted == true)
    }

    @Test("deleteBabyFromList navigates to babyProfile when last baby deleted")
    func deleteBabyFromList_lastBaby_navigatesToBabyProfile() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        let baby = Baby(accountId: "acct-1", name: "Only Baby")
        store.savedBabies = [baby]
        store.navigateToStep(.babyAdded)

        store.deleteBabyFromList(baby)

        #expect(store.savedBabies.isEmpty)
        #expect(store.currentStep == .babyProfile)
    }

    @Test("deleteBabyFromList with multiple babies stays on babyAdded")
    func deleteBabyFromList_multipleBabies_staysOnBabyAdded() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        let baby1 = Baby(accountId: "acct-1", name: "Baby 1")
        let baby2 = Baby(accountId: "acct-1", name: "Baby 2")
        store.savedBabies = [baby1, baby2]
        store.navigateToStep(.babyAdded)

        store.deleteBabyFromList(baby1)

        #expect(store.savedBabies.count == 1)
        #expect(store.currentStep == .babyAdded)
    }

    // MARK: - Action Handlers

    @Test("handleNextButtonClick on intro moves to next step")
    func handleNextButtonClick_intro_movesToNextStep() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.handleNextButtonClick()
        #expect(store.currentStep == .permissions)
    }

    @Test("handleNextButtonClick on permissions moves to next step")
    func handleNextButtonClick_permissions_movesToNextStep() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.permissions.rawValue
        store.handleNextButtonClick()
        #expect(store.currentStep == .wakeup)
    }

    @Test("handleNextButtonClick on babyProfile triggers saveBabyProfile")
    func handleNextButtonClick_babyProfile_triggersSave() async {
        let sut = makeSUT()
        let store = sut.store
        let babyService = sut.babyService
        store.scaleItem = makeScaleItem()
        store.navigateToStep(.babyProfile)
        store.babyProfileForm.name.value = "Save Me"

        store.handleNextButtonClick()

        let saved = await waitUntil { babyService.saveBabyCalls == 1 }
        #expect(saved == true)
    }

    @Test("handleNextButtonClick on babyAdded calls handleFinish")
    func handleNextButtonClick_babyAdded_callsFinish() {
        var dismissed = false
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.dismissAction = { dismissed = true }
        store.navigateToStep(.babyAdded)

        store.handleNextButtonClick()

        #expect(dismissed == true)
    }

    @Test("handleExit shows confirmation alert")
    func handleExit_showsAlert() {
        let sut = makeSUT()
        let store = sut.store
        let notification = sut.notification
        store.scaleItem = makeScaleItem()
        store.handleExit()
        #expect(notification.showAlertCalls == 1)
        #expect(notification.alertData?.title == "Exit Setup?")
    }

    @Test("handleExit does nothing when already exiting")
    func handleExit_alreadyExiting_doesNothing() {
        let sut = makeSUT()
        let store = sut.store
        let notification = sut.notification
        store.scaleItem = makeScaleItem()
        store.isExiting = true
        store.handleExit()
        #expect(notification.showAlertCalls == 0)
    }

    @Test("tryAgainButtonHandler resets error and navigates to wakeup")
    func tryAgainButtonHandler_resetsAndNavigates() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.navigateToStep(.connectingBluetooth)
        store.scaleSetupError = .connectionFailed
        store.connectionState = .failure

        store.tryAgainButtonHandler()

        #expect(store.scaleSetupError == .none)
        #expect(store.connectionState == .loading)
        #expect(store.currentStep == .wakeup)
    }

    // MARK: - Next Button Text

    @Test("nextButtonText returns correct text for each step")
    func nextButtonText_correctPerStep() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        let lang = BabyScaleSetupStrings.self

        #expect(store.nextButtonText == lang.Buttons.next) // intro

        store.currentStepIndex = BabyScaleSetupStep.paired.rawValue
        #expect(store.nextButtonText == lang.Buttons.continueButton)

        store.currentStepIndex = BabyScaleSetupStep.babyProfile.rawValue
        #expect(store.nextButtonText == lang.Buttons.save)

        store.currentStepIndex = BabyScaleSetupStep.babyAdded.rawValue
        #expect(store.nextButtonText == lang.Buttons.finish)
    }

    // MARK: - Configuration

    @Test("configure resolves SKU to scaleItem")
    func configure_resolvesSku() {
        let sut = makeSUT()
        let store = sut.store
        store.configure(with: "0220")
        #expect(store.scaleItem != nil)
        #expect(store.scaleItem?.sku == "0220")
    }

    @Test("configure with discovered scale and permissions granted jumps to connectingBluetooth")
    func configure_withDiscoveredScale_permissionsGranted_jumpsToConnecting() {
        let permissions = MockPermissionsService()
        permissions.permissions = [
            .BLUETOOTH: .ENABLED,
            .BLUETOOTH_SWITCH: .ENABLED
        ]
        let sut = makeSUT(permissionsService: permissions)
        let store = sut.store
        let device = ScaleTestFixtures.makeDevice(id: "baby-scale-1")
        let event = makeDiscoveryEvent(device: device)

        store.configure(with: "0220", discoveredScale: device, discoveryEvent: event)

        #expect(store.currentStep == .connectingBluetooth)
    }

    @Test("configure with discovered scale and permissions not granted goes to permissions")
    func configure_withDiscoveredScale_permissionsNotGranted_goesToPermissions() {
        let sut = makeSUT()
        let store = sut.store
        let device = ScaleTestFixtures.makeDevice(id: "baby-scale-1")
        let event = makeDiscoveryEvent(device: device)

        store.configure(with: "0220", discoveredScale: device, discoveryEvent: event)

        #expect(store.currentStep == .permissions)
    }

    @Test("configure without discovered scale stays on intro")
    func configure_noDiscoveredScale_staysOnIntro() {
        let sut = makeSUT()
        let store = sut.store
        store.configure(with: "0220")
        #expect(store.currentStep == .intro)
    }

    @Test("configure sets bluetoothService.isSetupInProgress to true")
    func configure_setsSetupInProgress() {
        let sut = makeSUT()
        let store = sut.store
        let bluetooth = sut.bluetooth
        store.configure(with: "0220")
        #expect(bluetooth.isSetupInProgress == true)
    }

    // MARK: - Cleanup

    @Test("performExitCleanup sets isSetupInProgress to false")
    func performExitCleanup_setsSetupInProgressFalse() {
        let sut = makeSUT()
        let store = sut.store
        let bluetooth = sut.bluetooth
        store.scaleItem = makeScaleItem()
        bluetooth.isSetupInProgress = true

        store.performExitCleanup()

        #expect(bluetooth.isSetupInProgress == false)
    }

    @Test("performExitCleanup calls dismissAction")
    func performExitCleanup_callsDismissAction() {
        var dismissed = false
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.dismissAction = { dismissed = true }

        store.performExitCleanup()

        #expect(dismissed == true)
    }

    @Test("cleanup nils out dismissAction and cancels subscriptions")
    func cleanup_clearsReferences() {
        let sut = makeSUT()
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.dismissAction = { }

        store.cleanup()

        #expect(store.dismissAction == nil)
        #expect(store.cancellables.isEmpty)
        #expect(store.deviceDiscoveryCancellable == nil)
        #expect(store.scanTimeoutTask == nil)
    }

    // MARK: - arePermissionsEnabled

    @Test("arePermissionsEnabled returns true when both BT permissions enabled")
    func arePermissionsEnabled_bothEnabled_returnsTrue() {
        let permissions = MockPermissionsService()
        permissions.permissions = [
            .BLUETOOTH: .ENABLED,
            .BLUETOOTH_SWITCH: .ENABLED
        ]
        let sut = makeSUT(permissionsService: permissions)
        let store = sut.store
        #expect(store.arePermissionsEnabled() == true)
    }

    @Test("arePermissionsEnabled returns false when permissions nil")
    func arePermissionsEnabled_nil_returnsFalse() {
        let sut = makeSUT()
        let store = sut.store
        #expect(store.arePermissionsEnabled() == false)
    }

    @Test("arePermissionsEnabled returns false when only one permission enabled")
    func arePermissionsEnabled_oneEnabled_returnsFalse() {
        let permissions = MockPermissionsService()
        permissions.permissions = [
            .BLUETOOTH: .ENABLED,
            .BLUETOOTH_SWITCH: .DISABLED
        ]
        let sut = makeSUT(permissionsService: permissions)
        let store = sut.store
        #expect(store.arePermissionsEnabled() == false)
    }

    // MARK: - showHelpModal

    @Test("showHelpModal presents alert via notification service")
    func showHelpModal_presentsAlert() {
        let sut = makeSUT()
        let store = sut.store
        let notification = sut.notification
        store.scaleItem = makeScaleItem()
        store.showHelpModal()
        #expect(notification.showAlertCalls == 1)
        #expect(notification.alertData?.title == "Need Help?")
    }
}

// MARK: - Test Errors

private enum BabyScaleSetupTestError: Error {
    case genericFailure
}

// MARK: - makeSUT

private struct BabyScaleSetupStoreSUT {
    let store: BabyScaleSetupStore
    let notification: MockNotificationHelperService
    let permissions: MockPermissionsService
    let bluetooth: MockBluetoothService
    let account: MockAccountService
    let scale: MockScaleService
    let babyService: MockBabyService
}

@MainActor
private func makeSUT(
    notificationService: MockNotificationHelperService? = nil,
    permissionsService: MockPermissionsService? = nil,
    bluetoothService: MockBluetoothService? = nil,
    accountService: MockAccountService? = nil,
    scaleService: MockScaleService? = nil,
    babyService: MockBabyService? = nil
) -> BabyScaleSetupStoreSUT {
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
    DependencyContainer.shared.register(scale as ScaleServiceProtocol)
    DependencyContainer.shared.register(baby as BabyServiceProtocol)

    let store = BabyScaleSetupStore()
    store.notificationService = notification
    store.permissionsService = permissions
    store.bluetoothService = bluetooth
    store.accountService = account
    store.scaleService = scale
    store.babyService = baby

    return BabyScaleSetupStoreSUT(
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
private func makeScaleItem() -> ScaleItemInfo {
    guard let scale = SCALES.first(where: { $0.sku == "0220" }) ?? SCALES.first ?? BPMS.first else {
        fatalError("No scale items available for testing")
    }
    return scale
}

@MainActor
private func makeDiscoveryEvent(device: Device? = nil, isNew: Bool = true) -> DeviceDiscoveryEvent {
    let dev = device ?? ScaleTestFixtures.makeDevice(id: "baby-scale-1")
    let scaleInfo = ScaleItemInfo(productName: "Smart Baby Scale", sku: "0220", imgPath: "scale0220", setupType: .babyScale, bodyComp: false)
    return DeviceDiscoveryEvent(device: dev, deviceInfo: scaleInfo, protocolType: .R4, isNew: isNew)
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
