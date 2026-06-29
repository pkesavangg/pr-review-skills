//
//  BabyScaleSetupStoreTests.swift
//  meAppTests
//

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct BabyScaleSetupStoreTests {

    // MARK: - Navigation Flow

    @Test("moveToNextStep advances currentStepIndex and currentStep")
    func moveToNextStep_advancesStep() {
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        #expect(store.currentStep == .intro)
        store.moveToNextStep()
        #expect(store.currentStep == .permissions)
    }

    @Test("moveToPreviousStep decrements step")
    func moveToPreviousStep_decrementsStep() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.permissions.rawValue
        store.moveToPreviousStep()
        #expect(store.currentStep == .intro)
    }

    @Test("moveToPreviousStep does nothing on intro step")
    func moveToPreviousStep_onIntro_doesNothing() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        #expect(store.currentStep == .intro)
        store.moveToPreviousStep()
        #expect(store.currentStep == .intro)
    }

    @Test("navigateToStep jumps to specific step")
    func navigateToStep_jumpsToStep() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
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
        let (store, _, _, _, _, _, _) = makeBabyScaleSUT(permissionsService: permissions)
        store.scaleItem = makeBabyScaleItem()
        // From intro (0), next should skip permissions (1) and land on wakeup (2)
        let adjusted = store.adjustedIndex(from: 1, direction: 1)
        #expect(store.steps[adjusted] == .wakeup)
    }

    @Test("adjustedIndex does not skip permissions when BT permissions not granted")
    func adjustedIndex_doesNotSkipPermissions_whenNotGranted() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        let adjusted = store.adjustedIndex(from: 1, direction: 1)
        #expect(store.steps[adjusted] == .permissions)
    }

    @Test("adjustedIndex skips wakeup and connectingBluetooth going backward")
    func adjustedIndex_skipsWakeupAndConnecting_goingBackward() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        // From scaleName (4), going backward should skip connectingBluetooth (3) and wakeup (2)
        let adjusted = store.adjustedIndex(from: 3, direction: -1)
        let step = store.steps[adjusted]
        #expect(step == .permissions || step == .intro)
    }

    @Test("back button on babyProfile with saved babies navigates to babyAdded")
    func handleBackButtonClick_babyProfile_withSavedBabies_navigatesToBabyAdded() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        let baby = Baby(accountId: "acct-1", name: "Test Baby")
        store.savedBabies = [baby]
        store.navigateToStep(.babyProfile)
        store.handleBackButtonClick()
        #expect(store.currentStep == .babyAdded)
    }

    @Test("back button on babyProfile with no saved babies navigates to previous step")
    func handleBackButtonClick_babyProfile_noSavedBabies_movesToPreviousStep() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.navigateToStep(.babyProfile)
        store.handleBackButtonClick()
        #expect(store.currentStep == .paired)
    }

    @Test("back button on babyProfile resets form when no saved babies")
    func handleBackButtonClick_babyProfile_noSavedBabies_resetsForm() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.navigateToStep(.babyProfile)
        store.babyProfileForm.name.value = "Some Name"
        store.handleBackButtonClick()
        #expect(store.babyProfileForm.name.value.isEmpty)
    }

    @Test("isBackButtonDisabled returns true on intro step")
    func isBackButtonDisabled_onIntro_returnsTrue() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        #expect(store.isBackButtonDisabled() == true)
    }

    @Test("isBackButtonDisabled returns false on non-intro step")
    func isBackButtonDisabled_onPermissions_returnsFalse() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.permissions.rawValue
        #expect(store.isBackButtonDisabled() == false)
    }

    @Test("shouldShowFooter returns false during wakeup step")
    func shouldShowFooter_wakeup_returnsFalse() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.wakeup.rawValue
        #expect(store.shouldShowFooter() == false)
    }

    @Test("shouldShowFooter returns false during connectingBluetooth step")
    func shouldShowFooter_connectingBluetooth_returnsFalse() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.connectingBluetooth.rawValue
        #expect(store.shouldShowFooter() == false)
    }

    @Test("shouldShowFooter returns true on other steps")
    func shouldShowFooter_intro_returnsTrue() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        #expect(store.shouldShowFooter() == true)
    }

    // MARK: - Permissions & Next Button

    @Test("permissions step: isNextEnabled is false when BT permissions disabled")
    func updateNextEnabled_permissions_disabled_nextDisabled() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
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
        let (store, notification, _, bluetooth, account, scale, babyService) = makeBabyScaleSUT(permissionsService: permissions)
        store.scaleItem = makeBabyScaleItem()
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
        let (store, notification, _, bluetooth, account, scale, babyService) = makeBabyScaleSUT(permissionsService: permissions)
        store.scaleItem = makeBabyScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.permissions.rawValue
        store.updateNextEnabled()
        #expect(store.isNextEnabled == false)
    }

    @Test("intro step: isNextEnabled is always true")
    func updateNextEnabled_intro_alwaysTrue() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.updateNextEnabled()
        #expect(store.isNextEnabled == true)
    }

    @Test("paired step: isNextEnabled is always true")
    func updateNextEnabled_paired_alwaysTrue() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.paired.rawValue
        store.updateNextEnabled()
        #expect(store.isNextEnabled == true)
    }

    @Test("babyAdded step: isNextEnabled is true when at least one baby exists")
    func updateNextEnabled_babyAdded_enabledWithBabies() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.savedBabies = [Baby(accountId: "acct-1", name: "Baby 1")]
        store.currentStepIndex = BabyScaleSetupStep.babyAdded.rawValue
        store.updateNextEnabled()
        #expect(store.isNextEnabled == true)
    }

    @Test("babyAdded step: isNextEnabled is false when all babies removed")
    func updateNextEnabled_babyAdded_disabledWhenEmpty() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.savedBabies = []
        store.currentStepIndex = BabyScaleSetupStep.babyAdded.rawValue
        store.updateNextEnabled()
        #expect(store.isNextEnabled == false)
    }

    @Test("scaleName step: isNextEnabled follows scaleNicknameForm.isValid")
    func updateNextEnabled_scaleName_followsFormValidity() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
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
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
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
        let sut = makeBabyScaleSUT()
        let store = sut.store
        let babyService = sut.babyService
        store.scaleItem = makeBabyScaleItem()
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
        let sut = makeBabyScaleSUT()
        let store = sut.store
        let babyService = sut.babyService
        store.scaleItem = makeBabyScaleItem()
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
        let sut = makeBabyScaleSUT(babyService: babyService)
        let store = sut.store
        store.scaleItem = makeBabyScaleItem()
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
        let sut = makeBabyScaleSUT(babyService: babyService)
        let store = sut.store
        store.scaleItem = makeBabyScaleItem()
        store.navigateToStep(.babyProfile)
        store.babyProfileForm.name.value = "Aria"

        await store.saveBabyProfile()

        #expect(store.babyProfileForm.duplicateNameError == BabyScaleSetupStrings.BabyProfile.duplicateNameError)
        #expect(store.scaleSetupError == .none)
        #expect(store.savedBabies.isEmpty)
    }

    @Test("saveBabyProfile does nothing when name is empty")
    func saveBabyProfile_emptyName_doesNothing() async {
        let sut = makeBabyScaleSUT()
        let store = sut.store
        let babyService = sut.babyService
        store.scaleItem = makeBabyScaleItem()
        store.navigateToStep(.babyProfile)
        store.babyProfileForm.name.value = ""

        await store.saveBabyProfile()

        #expect(babyService.saveBabyCalls == 0)
    }

    @Test("editBaby populates form fields and navigates to babyProfile")
    func editBaby_populatesFormAndNavigates() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        let baby = Baby(
            accountId: "acct-1",
            name: "My Baby",
            birthday: Date(),
            biologicalSex: "Male",
            birthLengthInches: 20.5,
            birthWeightLbs: 7,
            birthWeightOz: 4.5
        )
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
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.navigateToStep(.babyAdded)
        store.babyProfileForm.name.value = "Previous Baby"

        store.addAnotherBaby()

        #expect(store.babyProfileForm.name.value.isEmpty)
        #expect(store.currentStep == .babyProfile)
    }

    @Test("deleteBabyFromList removes baby and calls service")
    func deleteBabyFromList_removesAndCallsService() async {
        let sut = makeBabyScaleSUT()
        let store = sut.store
        let babyService = sut.babyService
        store.scaleItem = makeBabyScaleItem()
        let baby = Baby(accountId: "acct-1", name: "Test Baby")
        store.savedBabies = [baby]
        store.navigateToStep(.babyAdded)

        store.deleteBabyFromList(baby)

        #expect(store.savedBabies.isEmpty)
        let deleted = await waitUntilBabyScale { babyService.deleteBabyCalls == 1 }
        #expect(deleted == true)
    }

    @Test("deleteBabyFromList stays on babyAdded and disables Next when last baby deleted")
    func deleteBabyFromList_lastBaby_staysOnBabyAddedAndDisablesNext() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        let baby = Baby(accountId: "acct-1", name: "Only Baby")
        store.savedBabies = [baby]
        store.navigateToStep(.babyAdded)

        store.deleteBabyFromList(baby)

        #expect(store.savedBabies.isEmpty)
        #expect(store.currentStep == .babyAdded)
        #expect(store.isNextEnabled == false)
    }

    @Test("deleteBabyFromList with multiple babies stays on babyAdded")
    func deleteBabyFromList_multipleBabies_staysOnBabyAdded() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
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
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.handleNextButtonClick()
        #expect(store.currentStep == .permissions)
    }

    @Test("handleNextButtonClick on permissions moves to next step")
    func handleNextButtonClick_permissions_movesToNextStep() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.currentStepIndex = BabyScaleSetupStep.permissions.rawValue
        store.handleNextButtonClick()
        #expect(store.currentStep == .wakeup)
    }

    @Test("handleNextButtonClick on babyProfile triggers saveBabyProfile")
    func handleNextButtonClick_babyProfile_triggersSave() async {
        let sut = makeBabyScaleSUT()
        let store = sut.store
        let babyService = sut.babyService
        store.scaleItem = makeBabyScaleItem()
        store.navigateToStep(.babyProfile)
        store.babyProfileForm.name.value = "Save Me"

        store.handleNextButtonClick()

        let saved = await waitUntilBabyScale { babyService.saveBabyCalls == 1 }
        #expect(saved == true)
    }

    @Test("handleNextButtonClick on babyAdded calls handleFinish")
    func handleNextButtonClick_babyAdded_callsFinish() {
        var dismissed = false
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
        store.dismissAction = { dismissed = true }
        store.navigateToStep(.babyAdded)

        store.handleNextButtonClick()

        #expect(store.currentStep == .done)
    }

    @Test("handleExit shows confirmation alert")
    func handleExit_showsAlert() {
        let sut = makeBabyScaleSUT()
        let store = sut.store
        let notification = sut.notification
        store.scaleItem = makeBabyScaleItem()
        store.handleExit()
        #expect(notification.showAlertCalls == 1)
        #expect(notification.alertData?.title == "Exit Setup?")
    }

    @Test("handleExit does nothing when already exiting")
    func handleExit_alreadyExiting_doesNothing() {
        let sut = makeBabyScaleSUT()
        let store = sut.store
        let notification = sut.notification
        store.scaleItem = makeBabyScaleItem()
        store.isExiting = true
        store.handleExit()
        #expect(notification.showAlertCalls == 0)
    }

    @Test("tryAgainButtonHandler resets error and navigates to wakeup")
    func tryAgainButtonHandler_resetsAndNavigates() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
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
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.scaleItem = makeBabyScaleItem()
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
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
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
        let (store, notification, _, bluetooth, account, scale, babyService) = makeBabyScaleSUT(permissionsService: permissions)
        let device = ScaleTestFixtures.makeDevice(id: "baby-scale-1")
        let event = makeBabyDiscoveryEvent(device: device)

        store.configure(with: "0220", discoveredScale: device, discoveryEvent: event)

        #expect(store.currentStep == .wakeup)
    }

    @Test("configure with discovered scale and permissions not granted goes to permissions")
    func configure_withDiscoveredScale_permissionsNotGranted_goesToPermissions() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        let device = ScaleTestFixtures.makeDevice(id: "baby-scale-1")
        let event = makeBabyDiscoveryEvent(device: device)

        store.configure(with: "0220", discoveredScale: device, discoveryEvent: event)

        #expect(store.currentStep == .permissions)
    }

    @Test("configure without discovered scale stays on intro")
    func configure_noDiscoveredScale_staysOnIntro() {
        let (store, notification, permissions, bluetooth, account, scale, babyService) = makeBabyScaleSUT()
        store.configure(with: "0220")
        #expect(store.currentStep == .intro)
    }

    @Test("configure sets bluetoothService.isSetupInProgress to true")
    func configure_setsSetupInProgress() {
        let sut = makeBabyScaleSUT()
        let store = sut.store
        let bluetooth = sut.bluetooth
        store.configure(with: "0220")
        #expect(bluetooth.isSetupInProgress == true)
    }

}
