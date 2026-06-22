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
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        #expect(store.currentStep == .intro)
        store.moveToNextStep()
        #expect(store.currentStep == .permissions)
    }

    @Test("moveToPreviousStep decrements step")
    func moveToPreviousStep_decrementsStep() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.permissions.rawValue
        store.moveToPreviousStep()
        #expect(store.currentStep == .intro)
    }

    @Test("moveToPreviousStep does nothing on intro step")
    func moveToPreviousStep_onIntro_doesNothing() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
        store.scaleItem = makeScaleItem()
        #expect(store.currentStep == .intro)
        store.moveToPreviousStep()
        #expect(store.currentStep == .intro)
    }

    @Test("navigateToStep jumps to specific step")
    func navigateToStep_jumpsToStep() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
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
        let (store, _, _, _, _, _, _) = makeSUT(permissionsService: permissions)
        store.scaleItem = makeScaleItem()
        // From intro (0), next should skip permissions (1) and land on wakeup (2)
        let adjusted = store.adjustedIndex(from: 1, direction: 1)
        #expect(store.steps[adjusted] == .wakeup)
    }

    @Test("adjustedIndex does not skip permissions when BT permissions not granted")
    func adjustedIndex_doesNotSkipPermissions_whenNotGranted() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
        store.scaleItem = makeScaleItem()
        let adjusted = store.adjustedIndex(from: 1, direction: 1)
        #expect(store.steps[adjusted] == .permissions)
    }

    @Test("adjustedIndex skips wakeup and connectingBluetooth going backward")
    func adjustedIndex_skipsWakeupAndConnecting_goingBackward() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
        store.scaleItem = makeScaleItem()
        // From scaleName (4), going backward should skip connectingBluetooth (3) and wakeup (2)
        let adjusted = store.adjustedIndex(from: 3, direction: -1)
        let step = store.steps[adjusted]
        #expect(step == .permissions || step == .intro)
    }

    @Test("back button on babyProfile with saved babies navigates to babyAdded")
    func handleBackButtonClick_babyProfile_withSavedBabies_navigatesToBabyAdded() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
        store.scaleItem = makeScaleItem()
        let baby = Baby(accountId: "acct-1", name: "Test Baby")
        store.savedBabies = [baby]
        store.navigateToStep(.babyProfile)
        store.handleBackButtonClick()
        #expect(store.currentStep == .babyAdded)
    }

    @Test("back button on babyProfile with no saved babies navigates to previous step")
    func handleBackButtonClick_babyProfile_noSavedBabies_movesToPreviousStep() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.navigateToStep(.babyProfile)
        store.handleBackButtonClick()
        #expect(store.currentStep == .paired)
    }

    @Test("back button on babyProfile resets form when no saved babies")
    func handleBackButtonClick_babyProfile_noSavedBabies_resetsForm() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.navigateToStep(.babyProfile)
        store.babyProfileForm.name.value = "Some Name"
        store.handleBackButtonClick()
        #expect(store.babyProfileForm.name.value == "")
    }

    @Test("isBackButtonDisabled returns true on intro step")
    func isBackButtonDisabled_onIntro_returnsTrue() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
        store.scaleItem = makeScaleItem()
        #expect(store.isBackButtonDisabled() == true)
    }

    @Test("isBackButtonDisabled returns false on non-intro step")
    func isBackButtonDisabled_onPermissions_returnsFalse() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.permissions.rawValue
        #expect(store.isBackButtonDisabled() == false)
    }

    @Test("shouldShowFooter returns false during wakeup step")
    func shouldShowFooter_wakeup_returnsFalse() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.wakeup.rawValue
        #expect(store.shouldShowFooter() == false)
    }

    @Test("shouldShowFooter returns false during connectingBluetooth step")
    func shouldShowFooter_connectingBluetooth_returnsFalse() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.connectingBluetooth.rawValue
        #expect(store.shouldShowFooter() == false)
    }

    @Test("shouldShowFooter returns true on other steps")
    func shouldShowFooter_intro_returnsTrue() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
        store.scaleItem = makeScaleItem()
        #expect(store.shouldShowFooter() == true)
    }

    // MARK: - Permissions & Next Button

    @Test("permissions step: isNextEnabled is false when BT permissions disabled")
    func updateNextEnabled_permissions_disabled_nextDisabled() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
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
        let (store, notification, _, bluetooth, account, scale, babyService) = makeSUT(permissionsService: permissions)
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
        let (store, notification, _, bluetooth, account, scale, babyService) = makeSUT(permissionsService: permissions)
        store.scaleItem = makeScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.permissions.rawValue
        store.updateNextEnabled()
        #expect(store.isNextEnabled == false)
    }

    @Test("intro step: isNextEnabled is always true")
    func updateNextEnabled_intro_alwaysTrue() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.updateNextEnabled()
        #expect(store.isNextEnabled == true)
    }

    @Test("paired step: isNextEnabled is always true")
    func updateNextEnabled_paired_alwaysTrue() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.paired.rawValue
        store.updateNextEnabled()
        #expect(store.isNextEnabled == true)
    }

    @Test("babyAdded step: isNextEnabled is always true")
    func updateNextEnabled_babyAdded_alwaysTrue() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.babyAdded.rawValue
        store.updateNextEnabled()
        #expect(store.isNextEnabled == true)
    }

    @Test("scaleName step: isNextEnabled follows scaleNicknameForm.isValid")
    func updateNextEnabled_scaleName_followsFormValidity() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
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
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.babyProfile.rawValue

        // Empty name should be invalid
        store.babyProfileForm.name.value = ""
        store.babyProfileForm.biologicalSex.value = ""
        store.updateNextEnabled()
        #expect(store.isNextEnabled == false)

        // Valid required fields should be valid
        store.babyProfileForm.name.value = "Baby Name"
        store.babyProfileForm.biologicalSex.value = "Male"
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

    @Test("saveBabyProfile HTTP 409: sets duplicateNameError instead of generic error")
    func saveBabyProfile_409Error_setsDuplicateNameError() async {
        let babyService = MockBabyService()
        babyService.saveBabyError = HTTPError.statusCode(409)
        let sut = makeSUT(babyService: babyService)
        let store = sut.store
        store.scaleItem = makeScaleItem()
        store.navigateToStep(.babyProfile)
        store.babyProfileForm.name.value = "Aria"

        await store.saveBabyProfile()

        #expect(store.babyProfileForm.duplicateNameError == BabyScaleSetupStrings.BabyProfile.duplicateNameError)
        #expect(store.scaleSetupError == .none)
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
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
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
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
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
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
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
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
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
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.handleNextButtonClick()
        #expect(store.currentStep == .permissions)
    }

    @Test("handleNextButtonClick on permissions moves to next step")
    func handleNextButtonClick_permissions_movesToNextStep() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
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
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
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
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
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
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
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
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
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
        let (store, notification, _, bluetooth, account, scale, babyService) = makeSUT(permissionsService: permissions)
        let device = ScaleTestFixtures.makeDevice(id: "baby-scale-1")
        let event = makeDiscoveryEvent(device: device)

        store.configure(with: "0220", discoveredScale: device, discoveryEvent: event)

        #expect(store.currentStep == .connectingBluetooth)
    }

    @Test("configure with discovered scale and permissions not granted goes to permissions")
    func configure_withDiscoveredScale_permissionsNotGranted_goesToPermissions() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
        let device = ScaleTestFixtures.makeDevice(id: "baby-scale-1")
        let event = makeDiscoveryEvent(device: device)

        store.configure(with: "0220", discoveredScale: device, discoveryEvent: event)

        #expect(store.currentStep == .permissions)
    }

    @Test("configure without discovered scale stays on intro")
    func configure_noDiscoveredScale_staysOnIntro() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
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
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.dismissAction = { dismissed = true }

        store.performExitCleanup()

        #expect(dismissed == true)
    }

    @Test("cleanup nils out dismissAction and cancels subscriptions")
    func cleanup_clearsReferences() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
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
        let (store, notification, _, bluetooth, account, scale, babyService) = makeSUT(permissionsService: permissions)
        #expect(store.arePermissionsEnabled() == true)
    }

    @Test("arePermissionsEnabled returns false when permissions nil")
    func arePermissionsEnabled_nil_returnsFalse() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeSUT()
        #expect(store.arePermissionsEnabled() == false)
    }

    @Test("arePermissionsEnabled returns false when only one permission enabled")
    func arePermissionsEnabled_oneEnabled_returnsFalse() {
        let permissions = MockPermissionsService()
        permissions.permissions = [
            .BLUETOOTH: .ENABLED,
            .BLUETOOTH_SWITCH: .DISABLED
        ]
        let (store, notification, _, bluetooth, account, scale, babyService) = makeSUT(permissionsService: permissions)
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

    // MARK: - Skip Dialog Flow (MA-3617)

    @Test("showSkipBabyProfileDialog sets showSkipDialog to true")
    func showSkipBabyProfileDialog_setsFlag() {
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()

        store.showSkipBabyProfileDialog()

        #expect(store.showSkipDialog == true)
    }

    @Test("handleSkipConfirmed dismisses dialog and calls handleFinish")
    func handleSkipConfirmed_dismissesAndFinishes() {
        var dismissed = false
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.dismissAction = { dismissed = true }
        store.showSkipDialog = true

        store.handleSkipConfirmed()

        #expect(store.showSkipDialog == false)
        #expect(dismissed == true)
    }

    @Test("handleSkipCancelled dismisses dialog without finishing")
    func handleSkipCancelled_dismissesOnly() {
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.showSkipDialog = true

        store.handleSkipCancelled()

        #expect(store.showSkipDialog == false)
    }

    // MARK: - Skip Edit Baby Dialog

    @Test("showSkipBabyProfileDialog with editingBaby sets showSkipEditDialog")
    func showSkipBabyProfileDialog_withEditingBaby_setsEditFlag() {
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.editingBaby = Baby(accountId: "acct-1", name: "Aria")

        store.showSkipBabyProfileDialog()

        #expect(store.showSkipEditDialog == true)
        #expect(store.showSkipDialog == false)
    }

    @Test("showSkipBabyProfileDialog without editingBaby sets showSkipDialog")
    func showSkipBabyProfileDialog_noEditingBaby_setsAddFlag() {
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.editingBaby = nil

        store.showSkipBabyProfileDialog()

        #expect(store.showSkipDialog == true)
        #expect(store.showSkipEditDialog == false)
    }

    @Test("handleSkipEditConfirmed clears dialog, editingBaby, resets form, and navigates to babyAdded")
    func handleSkipEditConfirmed_clearsAndNavigates() {
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.editingBaby = Baby(accountId: "acct-1", name: "Aria")
        store.babyProfileForm.name.value = "Aria"
        store.showSkipEditDialog = true

        store.handleSkipEditConfirmed()

        #expect(store.showSkipEditDialog == false)
        #expect(store.editingBaby == nil)
        #expect(store.babyProfileForm.name.value == "")
        #expect(store.currentStep == .babyAdded)
    }

    @Test("handleSkipEditCancelled dismisses skip-edit dialog only")
    func handleSkipEditCancelled_dismissesOnly() {
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.showSkipEditDialog = true

        store.handleSkipEditCancelled()

        #expect(store.showSkipEditDialog == false)
    }

    // MARK: - confirmDeleteBabyFromList (MA-3617)

    @Test("confirmDeleteBabyFromList shows delete confirmation alert")
    func confirmDeleteBabyFromList_showsAlert() {
        let (store, notification, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        let baby = Baby(accountId: "acct-1", name: "Delete Me")

        store.confirmDeleteBabyFromList(baby)

        #expect(notification.showAlertCalls == 1)
    }

    // MARK: - handleFinish (MA-3617)

    @Test("handleFinish when scale already saved calls performExitCleanup directly")
    func handleFinish_scaleSaved_exitsDirectly() {
        var dismissed = false
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.isScaleSaved = true
        store.dismissAction = { dismissed = true }

        store.handleFinish()

        #expect(dismissed == true)
    }

    @Test("handleFinish when no discovered scale calls performExitCleanup directly")
    func handleFinish_noDiscoveredScale_exitsDirectly() {
        var dismissed = false
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.discoveredScale = nil
        store.discoveryEvent = nil
        store.dismissAction = { dismissed = true }

        store.handleFinish()

        #expect(dismissed == true)
    }

    // MARK: - editBaby with nil optional fields (MA-3617)

    @Test("editBaby with nil optional fields sets empty strings for optional form fields")
    func editBaby_nilOptionalFields_setsEmptyStrings() {
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        let baby = Baby(accountId: "acct-1", name: "Simple Baby")
        store.savedBabies = [baby]

        store.editBaby(baby)

        #expect(store.editingBaby?.id == baby.id)
        #expect(store.babyProfileForm.name.value == "Simple Baby")
        #expect(store.babyProfileForm.biologicalSex.value == "")
        #expect(store.babyProfileForm.birthLengthInches.value == "")
        #expect(store.babyProfileForm.birthWeightLbs.value == "")
        #expect(store.babyProfileForm.birthWeightOz.value == "")
        #expect(store.currentStep == .babyProfile)
    }

    // MARK: - handleStepChange (MA-3627)

    @Test("handleStepChange on wakeup starts bluetooth scan")
    func handleStepChange_wakeup_startsScan() async {
        let (store, _, _, bluetooth, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()

        store.navigateToStep(.wakeup)

        await waitUntil { bluetooth.scanForPairingCalls == 1 }
        #expect(bluetooth.scanForPairingCalls == 1)
    }

    @Test("handleStepChange on connectingBluetooth with no discovery sets failure")
    func handleStepChange_connectingBT_noDiscovery_setsFailure() {
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.discoveredScale = nil
        store.discoveryEvent = nil

        store.navigateToStep(.connectingBluetooth)

        #expect(store.connectionState == .failure)
    }

    @Test("handleStepChange does nothing when isExiting")
    func handleStepChange_isExiting_doesNothing() {
        let (store, _, _, bluetooth, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.isExiting = true

        store.currentStepIndex = BabyScaleSetupStep.wakeup.rawValue

        #expect(bluetooth.scanForPairingCalls == 0)
    }

    // MARK: - handleDeviceDiscovery (MA-3627)

    @Test("handleDeviceDiscovery ignores non-babyScale devices")
    func handleDeviceDiscovery_nonBabyScale_ignored() {
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.navigateToStep(.wakeup)

        let device = ScaleTestFixtures.makeDevice(id: "non-baby-1")
        let scaleInfo = ScaleItemInfo(productName: "Regular Scale", sku: "0100", imgPath: "scale0100", setupType: .bluetooth, bodyComp: false)
        let event = DeviceDiscoveryEvent(device: device.toSnapshot(), deviceInfo: scaleInfo, protocolType: .R4, isNew: true)

        store.handleDeviceDiscovery(event)

        #expect(store.discoveredScale == nil)
    }

    @Test("handleDeviceDiscovery ignores events when not on wakeup step")
    func handleDeviceDiscovery_notOnWakeup_ignored() {
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        // Stay on intro step
        let event = makeDiscoveryEvent()

        store.handleDeviceDiscovery(event)

        #expect(store.discoveredScale == nil)
    }

    @Test("handleDeviceDiscovery for new baby scale sets discovered state and starts pairing")
    func handleDeviceDiscovery_newBabyScale_startsPairing() async {
        let bluetooth = MockBluetoothService()
        bluetooth.confirmSmartPairResult = .success(.creationCompleted)
        let (store, _, _, _, _, _, _) = makeSUT(bluetoothService: bluetooth)
        store.scaleItem = makeScaleItem()
        store.navigateToStep(.wakeup)

        let event = makeDiscoveryEvent(isNew: true)
        store.handleDeviceDiscovery(event)

        #expect(store.discoveredScale != nil)
        #expect(store.discoveryEvent != nil)
        #expect(store.connectionState == .loading)
    }

    @Test("handleDeviceDiscovery for known baby scale shows alert")
    func handleDeviceDiscovery_knownScale_showsAlert() {
        let (store, notification, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.navigateToStep(.wakeup)

        let event = makeDiscoveryEvent(isNew: false)
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
        let (store, _, _, _, _, scale, _) = makeSUT(bluetoothService: bluetooth, accountService: account)
        store.scaleItem = makeScaleItem()
        let device = ScaleTestFixtures.makeDevice(id: "baby-scale-1")
        store.discoveredScale = device
        store.discoveryEvent = makeDiscoveryEvent(device: device)

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
        let (store, _, _, _, _, _, _) = makeSUT(bluetoothService: bluetooth)
        store.scaleItem = makeScaleItem()
        let device = ScaleTestFixtures.makeDevice(id: "baby-scale-1")
        store.discoveredScale = device
        store.discoveryEvent = makeDiscoveryEvent(device: device)

        await store.confirmPair()

        #expect(store.connectionState == .failure)
        #expect(store.scaleSetupError == .pairingFailed)
        #expect(store.currentStep == .connectingBluetooth)
    }

    @Test("confirmPair error: sets connectionFailed and navigates to connectingBluetooth")
    func confirmPair_error_setsConnectionFailed() async {
        let bluetooth = MockBluetoothService()
        bluetooth.confirmSmartPairResult = .failure(.confirmPairFailed(BabyScaleSetupTestError.genericFailure))
        let (store, _, _, _, _, _, _) = makeSUT(bluetoothService: bluetooth)
        store.scaleItem = makeScaleItem()
        let device = ScaleTestFixtures.makeDevice(id: "baby-scale-1")
        store.discoveredScale = device
        store.discoveryEvent = makeDiscoveryEvent(device: device)

        await store.confirmPair()

        #expect(store.connectionState == .failure)
        #expect(store.scaleSetupError == .connectionFailed)
        #expect(store.currentStep == .connectingBluetooth)
    }

    @Test("confirmPair with missing discovery data: sets failure")
    func confirmPair_missingData_setsFailure() async {
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.discoveredScale = nil
        store.discoveryEvent = nil

        await store.confirmPair()

        #expect(store.connectionState == .failure)
    }

    // MARK: - resetDiscoveryState (MA-3627)

    @Test("resetDiscoveryState clears all discovery-related state")
    func resetDiscoveryState_clearsState() {
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.discoveredScale = ScaleTestFixtures.makeDevice(id: "test-1")
        store.discoveryEvent = makeDiscoveryEvent()

        store.resetDiscoveryState()

        #expect(store.discoveredScale == nil)
        #expect(store.discoveryEvent == nil)
        #expect(store.deviceDiscoveryCancellable == nil)
        #expect(store.scanTimeoutTask == nil)
    }

    // MARK: - handleNextButtonClick scaleName (MA-3627)

    @Test("handleNextButtonClick on scaleName updates nickname and moves to next step")
    func handleNextButtonClick_scaleName_updatesNicknameAndMoves() async {
        let (store, _, _, _, _, scale, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        let device = Device(id: "scale-1", accountId: "acct-1", deviceType: DeviceType.scale.rawValue, createdAt: "")
        store.savedScale = device
        store.navigateToStep(.scaleName)
        store.scaleNicknameForm.nickname.value = "My Baby Scale"

        store.handleNextButtonClick()

        let called = await waitUntil { scale.editDeviceCalls == 1 }
        #expect(called == true)
        #expect(store.currentStep == .paired)
    }

    // MARK: - handleExit after scaleName with saved scale (MA-3627)

    @Test("handleExit after scaleName with saved scale exits without alert")
    func handleExit_afterScaleName_savedScale_exitsDirectly() {
        var dismissed = false
        let (store, notification, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
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
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.navigateToStep(.babyAdded)

        store.handleBackButtonClick()

        #expect(store.currentStep == .babyProfile)
    }

    // MARK: - handleNextButtonClick on paired (MA-3627)

    @Test("handleNextButtonClick on paired moves to babyProfile")
    func handleNextButtonClick_paired_movesToBabyProfile() {
        let (store, _, _, _, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.navigateToStep(.paired)

        store.handleNextButtonClick()

        #expect(store.currentStep == .babyProfile)
    }

    // MARK: - startBluetoothScan (MA-3627)

    @Test("startBluetoothScan resets discovery state and calls scanForPairing")
    func startBluetoothScan_resetsAndScans() async {
        let (store, _, _, bluetooth, _, _, _) = makeSUT()
        store.scaleItem = makeScaleItem()
        store.discoveredScale = ScaleTestFixtures.makeDevice(id: "old-1")

        store.startBluetoothScan()

        #expect(store.discoveredScale == nil)
        await waitUntil { bluetooth.scanForPairingCalls == 1 }
        #expect(bluetooth.scanForPairingCalls == 1)
        #expect(store.scanTimeoutTask != nil)
    }
}

// MARK: - Test Errors

private enum BabyScaleSetupTestError: Error {
    case genericFailure
}

// MARK: - makeSUT

// swiftlint:disable large_tuple
@MainActor
private func makeSUT(
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
    DependencyContainer.shared.register(scale as ScaleServiceProtocol)
    DependencyContainer.shared.register(baby as BabyServiceProtocol)

    let store = BabyScaleSetupStore()
    store.notificationService = notification
    store.permissionsService = permissions
    store.bluetoothService = bluetooth
    store.accountService = account
    store.scaleService = scale
    store.babyService = baby

    return (store: store, notification: notification, permissions: permissions, bluetooth: bluetooth, account: account, scale: scale, babyService: baby)
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
    return DeviceDiscoveryEvent(device: dev.toSnapshot(), deviceInfo: scaleInfo, protocolType: .R4, isNew: isNew)
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
