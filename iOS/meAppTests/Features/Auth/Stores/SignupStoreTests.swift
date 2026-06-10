import Foundation
import SwiftUI
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct SignupStoreTests {
    @Test("initial state")
    func initialState() {
        let (store, _, _, _) = makeSUT()

        #expect(store.currentStepIndex == stepIndex(.name, in: store))
        #expect(store.currentStep == .name)
        #expect(store.isNextEnabled == false)
        #expect(store.isGoalSkipped == false)
        #expect(store.progressValue > 0)
    }

    @Test("next button enabled for valid name step")
    func nextEnabledAtNameStep() {
        let (store, _, _, _) = makeSUT()

        store.signupForm.firstName.value = "John"
        store.signupForm.lastName.value = "Doe"
        store.updateNextButtonState()

        #expect(store.isNextEnabled == true)
    }

    @Test("next button state for date of birth step")
    func nextEnabledAtDateOfBirthStep() {
        let (store, _, _, _) = makeSUT()
        store.currentStepIndex = stepIndex(.dateOfBirth, in: store)

        store.signupForm.birthday.value = Date().addingTimeInterval(60 * 60 * 24)
        store.updateNextButtonState()
        #expect(store.isNextEnabled == false)

        store.signupForm.birthday.value = Date()
        store.updateNextButtonState()
        #expect(store.isNextEnabled == true)
    }

    @Test("next button state for sex step")
    func nextEnabledAtSexStep() {
        let (store, _, _, _) = makeSUT()
        store.currentStepIndex = stepIndex(.sex, in: store)

        store.signupForm.gender.value = ""
        store.updateNextButtonState()
        #expect(store.isNextEnabled == false)

        store.signupForm.gender.value = Sex.male.rawValue
        store.updateNextButtonState()
        #expect(store.isNextEnabled == true)
    }

    @Test("next button state for height step")
    func nextEnabledAtHeightStep() {
        let (store, _, _, _) = makeSUT()
        store.currentStepIndex = stepIndex(.height, in: store)

        store.signupForm.useMetric.value = true
        store.signupForm.height.value = Double(ConversionTools.convertCmToStoredHeight(99))
        store.updateNextButtonState()
        #expect(store.isNextEnabled == false)

        store.signupForm.height.value = Double(ConversionTools.convertCmToStoredHeight(170))
        store.updateNextButtonState()
        #expect(store.isNextEnabled == true)
    }

    @Test("next button state for goal step")
    func nextEnabledAtGoalStep() {
        let (store, _, _, _) = makeSUT()
        store.currentStepIndex = stepIndex(.goal, in: store)

        store.signupForm.goalType.value = GoalTypeSegment.losegainValue
        store.signupForm.currentWeight.value = "150"
        store.signupForm.goalWeight.value = "150"
        store.signupForm.currentWeight.markAsTouched()
        store.signupForm.goalWeight.markAsTouched()
        store.updateNextButtonState()
        #expect(store.isNextEnabled == false)

        store.signupForm.goalWeight.value = "160"
        store.updateNextButtonState()
        #expect(store.isNextEnabled == true)
    }

    @Test("next button state for email step")
    func nextEnabledAtEmailStep() {
        let (store, _, _, _) = makeSUT()
        store.currentStepIndex = stepIndex(.email, in: store)

        store.signupForm.email.value = "invalid"
        store.updateNextButtonState()
        #expect(store.isNextEnabled == false)

        store.signupForm.email.value = "valid@example.com"
        store.updateNextButtonState()
        #expect(store.isNextEnabled == true)
    }

    @Test("moveToNextStep advances through flow")
    func moveToNextStepAdvances() {
        let (store, _, _, _) = makeSUT()

        // New order: Name → Email → Birthday → …
        #expect(store.currentStep == .name)
        store.moveToNextStep()
        #expect(store.currentStep == .email)
    }

    @Test("moveToPreviousStep resets goal skipped when returning to goal")
    func moveToPreviousStepResetsGoalSkipped() {
        let (store, _, _, _) = makeSUT()
        // With new order Name→Email→Birthday→PickDevice→…→goal→password→profileReady,
        // navigating back from password lands on goal (for weightScale default flow)
        store.currentStepIndex = stepIndex(.password, in: store)
        store.isGoalSkipped = true

        store.moveToPreviousStep()

        #expect(store.currentStep == .goal)
        #expect(store.isGoalSkipped == false)
    }

    @Test("password step requires password match")
    func passwordStepValidation() {
        let (store, _, _, _) = makeSUT()
        store.currentStepIndex = stepIndex(.password, in: store)

        store.signupForm.password.value = "secret1"
        store.signupForm.confirmPassword.value = "secret2"
        store.signupForm.zipcode.value = "10001"
        store.signupForm.validate()
        store.updateNextButtonState()

        #expect(store.isNextEnabled == false)

        store.signupForm.confirmPassword.value = "secret1"
        store.signupForm.validate()
        store.updateNextButtonState()

        #expect(store.isNextEnabled == true)
    }

    @Test("touchAndValidate marks field touched")
    func touchAndValidateField() {
        let (store, _, _, _) = makeSUT()

        #expect(store.signupForm.email.isTouched == false)
        store.touchAndValidate(field: .email)
        #expect(store.signupForm.email.isTouched == true)
    }

    @Test("handleEditingChanged validates only on blur")
    func handleEditingChanged() {
        let (store, _, _, _) = makeSUT()

        store.handleEditingChanged(true, field: .zipCode)
        #expect(store.signupForm.zipcode.isTouched == false)

        store.handleEditingChanged(false, field: .zipCode)
        #expect(store.signupForm.zipcode.isTouched == true)
    }

    @Test("handleSkip resets goal and advances")
    func handleSkip() {
        let (store, _, _, _) = makeSUT()
        store.currentStepIndex = stepIndex(.goal, in: store)
        store.signupForm.currentWeight.value = "180"
        store.signupForm.goalWeight.value = "150"

        store.handleSkip()

        #expect(store.isGoalSkipped == true)
        #expect(store.signupForm.currentWeight.value == "")
        #expect(store.signupForm.goalWeight.value == "")
        // With new order, skipping goal advances to password (email is now before pickDevice)
        #expect(store.currentStepIndex == stepIndex(.password, in: store))
    }

    @Test("showHeightPicker toggles correct picker by unit")
    func showHeightPickerByUnit() {
        let (store, _, _, _) = makeSUT()

        store.signupForm.useMetric.value = false
        store.showHeightPicker()
        #expect(store.showHeightInchesPicker == true)

        store.showHeightInchesPicker = false
        store.signupForm.useMetric.value = true
        store.showHeightPicker()
        #expect(store.showHeightCmPicker == true)
    }

    @Test("updateFormHeight ignores invalid values")
    func updateFormHeightInvalidValues() {
        let (store, _, _, _) = makeSUT()
        let originalHeight = store.signupForm.height.value

        store.updateFormHeight(fromMetric: true, values: ["9"])
        #expect(store.signupForm.height.value == originalHeight)

        store.updateFormHeight(fromMetric: false, values: ["1", "0"])
        #expect(store.signupForm.height.value == originalHeight)
    }

    @Test("updateFormHeight accepts valid metric and imperial values")
    func updateFormHeightValidValues() {
        let (store, _, _, _) = makeSUT()

        store.updateFormHeight(fromMetric: true, values: ["1", "8", "0"])
        #expect(Int(store.signupForm.height.value) == ConversionTools.convertCmToStoredHeight(180))

        store.updateFormHeight(fromMetric: false, values: ["6", "2"])
        #expect(Int(store.signupForm.height.value) == ConversionTools.convertInchesToStoredHeight(74))
    }

    @Test("getFormattedHeight reflects selected unit")
    func formattedHeightByUnit() {
        let (store, _, _, _) = makeSUT()
        store.signupForm.height.value = 700

        store.signupForm.useMetric.value = false
        #expect(store.getFormattedHeight().contains("'"))

        store.signupForm.useMetric.value = true
        #expect(store.getFormattedHeight().contains("cm"))
    }

    @Test("pristine exit navigates back")
    func handleExitPristineNavigatesBack() {
        let (store, _, notificationService, _) = makeSUT()
        let router = Router<AuthRoute>()
        router.navigate(to: .signup)

        store.handleExit(router: router)

        #expect(router.stack.isEmpty == true)
        #expect(notificationService.isAlertVisible == false)
    }

    @Test("dirty exit shows alert")
    func handleExitDirtyShowsAlert() {
        let (store, _, notificationService, _) = makeSUT()
        store.signupForm.firstName.value = "John"

        store.handleExit()

        #expect(notificationService.isAlertVisible == true)
        #expect(notificationService.alertData?.buttons.count == 2)
    }

    @Test("dirty exit primary action navigates back and resets form")
    func handleExitDirtyPrimaryAction() {
        let (store, _, notificationService, _) = makeSUT()
        let router = Router<AuthRoute>()
        router.navigate(to: .signup)
        store.signupForm.firstName.value = "John"
        store.currentStepIndex = stepIndex(.password, in: store)

        store.handleExit(router: router)
        notificationService.alertData?.buttons.first?.action(nil)

        #expect(router.stack.isEmpty == true)
        #expect(store.currentStep == .name)
        #expect(store.signupForm.firstName.value == "")
    }

    @Test("dirty exit secondary action keeps current state")
    func handleExitDirtySecondaryAction() {
        let (store, _, notificationService, _) = makeSUT()
        store.currentStepIndex = stepIndex(.password, in: store)
        store.signupForm.firstName.value = "John"

        store.handleExit()
        notificationService.alertData?.buttons.last?.action(nil)

        #expect(store.currentStep == .password)
        #expect(store.signupForm.firstName.value == "John")
    }

    @Test("show help modal")
    func showHelpModal() {
        let (store, _, notificationService, _) = makeSUT()

        store.showHelpModal()

        #expect(notificationService.isModalVisible == true)
        #expect(notificationService.modalViewData.count == 1)
    }

    @Test("createUser success without goal for single device dismisses via completeSignup")
    func createUserSuccessWithoutGoal() async {
        let (store, accountService, notificationService, _) = makeSUT()
        accountService.signUpResult = .success(())

        var successCalled = false
        store.onSignupSuccess = { successCalled = true }

        fillRequiredSignupFields(store)
        store.isGoalSkipped = true
        // Single device → success screen is reserved for full coverage,
        // so signup falls through to completeSignup() and dismisses.
        store.selectedDeviceType = .weightScale

        await store.createUser()

        #expect(accountService.signUpCalls == 1)
        #expect(accountService.createGoalCalls == 0)
        #expect(accountService.lastSignUpEmail == "signup@example.com")
        #expect(successCalled == true)
        #expect(store.currentStep != .allProfilesReady)
        #expect(notificationService.isLoaderVisible == false)
    }

    @Test("createUser trims email and profile fields before API call")
    func createUserTrimsValuesBeforeAPICall() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.signUpResult = .success(())
        store.isGoalSkipped = true

        store.signupForm.firstName.value = "  John  "
        store.signupForm.lastName.value = "  Doe  "
        store.signupForm.email.value = "  signup@example.com  "
        store.signupForm.password.value = "secret123"
        store.signupForm.confirmPassword.value = "secret123"
        store.signupForm.zipcode.value = " 10001 "

        await store.createUser()

        #expect(accountService.signUpCalls == 1)
        #expect(accountService.lastSignUpEmail == "signup@example.com")
        #expect(accountService.lastSignUpProfile?.firstName == "John")
        #expect(accountService.lastSignUpProfile?.lastName == "Doe")
        #expect(accountService.lastSignUpProfile?.zipcode == "10001")
    }

    @Test("createUser success with goal creates derived goal for weight scale")
    func createUserSuccessWithGoal() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.signUpResult = .success(())
        accountService.createGoalResult = .success(())

        fillRequiredSignupFields(store)
        store.selectedDeviceType = .weightScale
        store.signupForm.goalType.value = GoalTypeSegment.losegainValue
        store.signupForm.currentWeight.value = "150"
        store.signupForm.goalWeight.value = "170"

        await store.createUser()

        #expect(accountService.signUpCalls == 1)
        #expect(accountService.createGoalCalls == 1)
        #expect(accountService.lastCreatedGoal?.goalType == .gain)
    }

    @Test("createUser success with maintain goal")
    func createUserSuccessWithMaintainGoal() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.signUpResult = .success(())
        accountService.createGoalResult = .success(())

        fillRequiredSignupFields(store)
        store.selectedDeviceType = .weightScale
        store.signupForm.goalType.value = GoalType.maintain.rawValue
        store.signupForm.goalWeight.value = "160"
        store.signupForm.currentWeight.value = "0"

        await store.createUser()

        #expect(accountService.createGoalCalls == 1)
        #expect(accountService.lastCreatedGoal?.goalType == .maintain)
    }

    @Test("createUser navigates to allProfilesReady only when all device types are registered")
    func createUserNavigatesToSuccessScreen() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.signUpResult = .success(())
        fillRequiredSignupFields(store)
        store.isGoalSkipped = true
        store.signupForm.gender.value = Sex.male.rawValue
        store.registeredDeviceTypes = [.weightScale, .bpm]
        store.selectedDeviceType = .babyScale

        await store.createUser()

        #expect(store.currentStep == .allProfilesReady)
    }

    @Test("createUser with partial device coverage dismisses via completeSignup")
    func createUserPartialDevicesCallsCompleteSignup() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.signUpResult = .success(())
        fillRequiredSignupFields(store)
        store.isGoalSkipped = true
        var successCalled = false
        store.onSignupSuccess = { successCalled = true }
        // Two of three device types — not full coverage.
        store.registeredDeviceTypes = [.weightScale]
        store.selectedDeviceType = .bpm

        await store.createUser()

        #expect(store.currentStep != .allProfilesReady)
        #expect(successCalled == true)
    }

    @Test("createUser navigates to signupError when the accumulated product-type write fails")
    func createUserNavigatesToErrorScreen() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.signUpResult = .success(())
        accountService.updateProductTypesResult = .failure(HTTPError.serverError)
        fillRequiredSignupFields(store)
        store.isGoalSkipped = true
        store.selectedDeviceType = .bpm

        await store.createUser()

        #expect(store.currentStep == .signupError)
        let failedCount = store.deviceStatuses.filter {
            if case .failure = $0.status { return true }
            return false
        }.count
        #expect(failedCount == 1)
    }

    @Test("completeSignup calls onSignupSuccess and resets form")
    func completeSignupCallsSuccessAndResets() {
        let (store, _, _, _) = makeSUT()
        var successCalled = false
        store.onSignupSuccess = { successCalled = true }
        store.signupForm.firstName.value = "John"

        store.completeSignup()

        #expect(successCalled == true)
        #expect(store.signupForm.firstName.value == "")
    }

    @Test("createUser from account switching with full device coverage shows success screen and defers callback")
    func createUserAccountSwitchingSkipsSuccessCallback() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.signUpResult = .success(())
        store.isGoalSkipped = true
        store.isFromAccountSwitching = true
        store.signupForm.gender.value = Sex.male.rawValue
        store.registeredDeviceTypes = [.weightScale, .bpm]
        store.selectedDeviceType = .babyScale
        fillRequiredSignupFields(store)

        var successCalled = false
        store.onSignupSuccess = { successCalled = true }

        await store.createUser()

        // With full device coverage, the success screen is shown and onSignupSuccess
        // is deferred until the user taps DONE (completeSignup).
        #expect(successCalled == false)
        #expect(store.currentStep == .allProfilesReady)
    }

    @Test("createUser max accounts reached shows alert")
    func createUserMaxAccountsReached() async {
        let (store, accountService, notificationService, _) = makeSUT()
        accountService.signUpResult = .failure(AccountError.maxAccountsReached)
        fillRequiredSignupFields(store)

        await store.createUser()

        #expect(notificationService.isAlertVisible == true)
        #expect(notificationService.alertData?.title == SignupStoreTestText.maxUsersAlertTitle)
        #expect(notificationService.alertData?.message == SignupStoreTestText.maxUsersLogInAndRemoveMessage)
    }

    @Test("createUser max accounts reached from account switching shows switching message")
    func createUserMaxAccountsReachedFromAccountSwitching() async {
        let (store, accountService, notificationService, _) = makeSUT()
        accountService.signUpResult = .failure(AccountError.maxAccountsReached)
        store.isFromAccountSwitching = true
        fillRequiredSignupFields(store)

        await store.createUser()

        #expect(notificationService.isAlertVisible == true)
        #expect(notificationService.alertData?.title == SignupStoreTestText.maxUsersAlertTitle)
        #expect(notificationService.alertData?.message == SignupStoreTestText.maxUsersSwitchingMessage)
    }

    @Test("createUser badRequest shows email in use toast")
    func createUserBadRequestToast() async {
        let (store, accountService, notificationService, _) = makeSUT()
        accountService.signUpResult = .failure(HTTPError.badRequest)
        fillRequiredSignupFields(store)

        await store.createUser()

        #expect(notificationService.toastData?.title == SignupStoreTestText.errorCreatingAccountTitle)
        #expect(notificationService.toastData?.message == SignupStoreTestText.emailInUseMessage)
    }

    @Test("createUser no internet shows no toast")
    func createUserNoInternetNoToast() async {
        let (store, accountService, notificationService, _) = makeSUT()
        accountService.signUpResult = .failure(HTTPError.noInternet)
        fillRequiredSignupFields(store)

        await store.createUser()

        #expect(notificationService.isToastVisible == false)
    }

    @Test("createUser api email in use shows mapped toast")
    func createUserEmailInUseAPIToast() async {
        let (store, accountService, notificationService, _) = makeSUT()
        accountService.signUpResult = .failure(HTTPError.apiError(message: SignupStoreTestText.emailAlreadyInUse, code: 400))
        fillRequiredSignupFields(store)

        await store.createUser()

        #expect(notificationService.toastData?.message == SignupStoreTestText.emailInUseMessage)
    }

    @Test("createUser server error shows server toast")
    func createUserServerErrorToast() async {
        let (store, accountService, notificationService, _) = makeSUT()
        accountService.signUpResult = .failure(HTTPError.serverError)
        fillRequiredSignupFields(store)

        await store.createUser()

        #expect(notificationService.toastData?.message == SignupStoreTestText.serverErrorMessage)
    }

    @Test("createUser unknown error shows generic toast")
    func createUserUnknownErrorToast() async {
        let (store, accountService, notificationService, _) = makeSUT()
        accountService.signUpResult = .failure(SignupStoreTestError.generic)
        fillRequiredSignupFields(store)

        await store.createUser()

        #expect(notificationService.toastData?.message == SignupStoreTestText.somethingWentWrongMessage)
    }

    @Test("password Complete creates the account and advances to profileReady")
    func passwordCompleteCreatesAccountAndAdvances() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.signUpResult = .success(())
        fillRequiredSignupFields(store)
        store.isGoalSkipped = true
        store.selectedDeviceType = .weightScale
        store.currentStepIndex = stepIndex(.password, in: store)

        await store.createAccountAtPassword()

        // Per MOB-419 the account is created when COMPLETE is tapped on the password
        // step, then the flow advances to the per-device profile-ready screen.
        #expect(accountService.signUpCalls == 1)
        #expect(store.isAccountCreated == true)
        #expect(store.currentStep == .profileReady)
    }

    @Test("password Complete account-creation failure keeps user on password with toast")
    func passwordCompleteFailureStaysOnPassword() async {
        let (store, accountService, notificationService, _) = makeSUT()
        accountService.signUpResult = .failure(HTTPError.serverError)
        fillRequiredSignupFields(store)
        store.isGoalSkipped = true
        store.selectedDeviceType = .weightScale
        store.currentStepIndex = stepIndex(.password, in: store)

        await store.createAccountAtPassword()

        // Account-creation errors stay as a toast on the password step — they are
        // NOT the per-device error screen.
        #expect(store.isAccountCreated == false)
        #expect(store.currentStep == .password)
        #expect(notificationService.toastData?.message == SignupStoreTestText.serverErrorMessage)
    }

    @Test("FINISH after account already created does not call signUp again")
    func finishAfterAccountCreatedSkipsSignUp() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.signUpResult = .success(())
        fillRequiredSignupFields(store)
        store.isGoalSkipped = true
        store.selectedDeviceType = .weightScale
        store.currentStepIndex = stepIndex(.password, in: store)

        await store.createAccountAtPassword()
        #expect(accountService.signUpCalls == 1)

        await store.createUser()

        // Account was already created at password Complete; FINISH only saves products.
        #expect(accountService.signUpCalls == 1)
    }

    @Test("CANCEL on error screen completes signup instead of discarding")
    func cancelOnErrorScreenCompletesSignup() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.signUpResult = .success(())
        accountService.updateProductTypesResult = .failure(HTTPError.serverError)
        fillRequiredSignupFields(store)
        store.isGoalSkipped = true
        store.selectedDeviceType = .bpm
        var successCalled = false
        store.onSignupSuccess = { successCalled = true }

        await store.createUser()
        #expect(store.currentStep == .signupError)

        // CANCEL → FINISH: complete with whatever saved and exit to dashboard.
        store.completeSignup()
        #expect(successCalled == true)
    }

    @Test("resetForm resets key state")
    func resetFormResetsState() {
        let (store, _, _, _) = makeSUT()
        store.currentStepIndex = stepIndex(.password, in: store)
        store.isGoalSkipped = true
        store.showHeightCmPicker = true
        store.signupForm.firstName.value = "John"

        store.resetForm()

        #expect(store.currentStep == .name)
        #expect(store.isGoalSkipped == false)
        #expect(store.showHeightCmPicker == false)
        #expect(store.signupForm.firstName.value == "")
    }

    // MARK: - Sequential Multi-Device Loop Tests

    @Test("steps excludes email and password on second device loop")
    func stepsExcludesEmailPasswordOnSecondLoop() {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.weightScale)
        store.signupForm.gender.value = Sex.male.rawValue
        store.connectAnotherDevice()
        store.selectDeviceType(.bpm)

        #expect(!store.steps.contains(.email))
        #expect(!store.steps.contains(.password))
        #expect(store.steps.contains(.profileReady))
    }

    @Test("steps includes sex step when gender not yet collected")
    func stepsIncludesSexWhenNotCollected() {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.bpm)

        #expect(store.steps.contains(.sex))
    }

    @Test("steps excludes sex step only on subsequent loop when gender already collected")
    func stepsExcludesSexWhenAlreadyCollected() {
        let (store, _, _, _) = makeSUT()
        // Sex is only omitted when it was collected in a PRIOR device loop.
        // Gender must be filled BEFORE entering the next loop so the rebuild on
        // selectDeviceType evaluates skipSex against a non-empty value.
        store.selectDeviceType(.weightScale)
        store.signupForm.gender.value = Sex.female.rawValue
        store.connectAnotherDevice()
        store.selectDeviceType(.bpm)

        #expect(!store.steps.contains(.sex))
    }

    @Test("changing gender mid-loop does not reshape steps under the user")
    func changingGenderMidLoopKeepsStepsStable() {
        // Reproduces the auto-nav bug: on a subsequent device loop the user lands
        // on .sex with gender empty. Tapping a sex option used to shrink the steps
        // array via the computed property and visually advance the swiper.
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.babyScale)
        store.connectAnotherDevice()
        store.selectDeviceType(.bpm)
        let stepsBefore = store.steps
        let indexBefore = store.currentStepIndex

        store.signupForm.gender.value = Sex.male.rawValue

        #expect(store.steps == stepsBefore)
        #expect(store.currentStepIndex == indexBefore)
    }

    @Test("selecting a sex on a subsequent device loop does not auto-advance from .sex")
    func selectingSexOnSubsequentLoopDoesNotAdvance() {
        // First loop: baby scale (no sex step) — gender stays empty.
        // Second loop: BPM — sex is collected. Tapping a sex option must keep
        // the user on .sex until they explicitly tap Next.
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.babyScale)
        store.connectAnotherDevice()
        store.selectDeviceType(.bpm)
        guard let sexIndex = store.steps.firstIndex(of: .sex) else {
            Issue.record("expected .sex in steps for BPM second loop")
            return
        }
        store.currentStepIndex = sexIndex

        store.signupForm.gender.value = Sex.male.rawValue

        #expect(store.currentStep == .sex)
        #expect(store.currentStepIndex == sexIndex)
    }

    @Test("profileReadyTitle uses per-device copy while more devices can be added")
    func profileReadyTitlePerDeviceWhileMoreRemain() {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.weightScale)
        #expect(store.profileReadyTitle == SignupStrings.ProfileReadyStep.weightScaleTitle)

        store.connectAnotherDevice()
        store.selectDeviceType(.babyScale)
        #expect(store.profileReadyTitle == SignupStrings.ProfileReadyStep.babyScaleTitle)
    }

    @Test("profileReadyTitle switches to all-profiles copy on the final device")
    func profileReadyTitleAllProfilesOnFinalDevice() {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.weightScale)
        store.connectAnotherDevice()
        store.selectDeviceType(.babyScale)
        store.connectAnotherDevice()
        store.selectDeviceType(.bpm)

        #expect(store.canConnectAnotherDevice == false)
        #expect(store.profileReadyTitle == SignupStrings.AllProfilesReadyStep.title)
    }

    @Test("canConnectAnotherDevice is true when fewer than 2 devices disabled")
    func canConnectAnotherDeviceWhenDevicesRemain() {
        let (store, _, _, _) = makeSUT()

        store.disabledDeviceTypes = []
        #expect(store.canConnectAnotherDevice == true)

        store.disabledDeviceTypes = [.weightScale]
        #expect(store.canConnectAnotherDevice == true)
    }

    @Test("canConnectAnotherDevice is false when all 3 device types are used")
    func cannotConnectAnotherDeviceWhenAllUsed() {
        let (store, _, _, _) = makeSUT()
        store.disabledDeviceTypes = [.weightScale, .bpm]

        #expect(store.canConnectAnotherDevice == false)
    }

    @Test("profileReady does not auto-finish even when all devices are registered")
    func profileReadyDoesNotAutoFinish() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.signUpResult = .success(())
        fillRequiredSignupFields(store)
        store.isGoalSkipped = true
        store.signupForm.gender.value = Sex.male.rawValue
        // First two devices recorded, third is current — full coverage.
        store.registeredDeviceTypes = [.weightScale, .babyScale]
        store.selectedDeviceType = .bpm
        store.disabledDeviceTypes = [.weightScale, .babyScale]

        // Landing on profileReady must not trigger signup; FINISH is required.
        if let profileReadyIndex = store.steps.firstIndex(of: .profileReady) {
            store.currentStepIndex = profileReadyIndex
        }

        // Give any stray async work a chance to run before asserting nothing fired.
        try? await Task.sleep(nanoseconds: 50_000_000)
        #expect(accountService.signUpCalls == 0)
        #expect(store.currentStep == .profileReady)

        // Explicit FINISH tap drives the success screen.
        store.finishSignup()
        await waitUntil {
            store.currentStep == .allProfilesReady || store.currentStep == .signupError
        }
        #expect(accountService.signUpCalls == 1)
        #expect(store.currentStep == .allProfilesReady)
    }

    @Test("connectAnotherDevice appends current device to registeredDeviceTypes")
    func connectAnotherDeviceAccumulatesRegistered() {
        let (store, _, _, _) = makeSUT()
        store.selectedDeviceType = .weightScale

        store.connectAnotherDevice()

        #expect(store.registeredDeviceTypes == [.weightScale])
        #expect(store.disabledDeviceTypes.contains(.weightScale))
    }

    @Test("createUser persists every registered device type in a single accumulated write")
    func createUserSendsAllRegisteredDeviceTypes() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.signUpResult = .success(())
        fillRequiredSignupFields(store)
        store.isGoalSkipped = true
        store.registeredDeviceTypes = [.weightScale]
        store.selectedDeviceType = .bpm

        await store.createUser()

        // The final accumulated write must contain every successful device's
        // product type so `updateProductTypes` (which replaces the array) doesn't
        // leave the account with only the last-written device.
        let lastSent = accountService.allUpdatedProductTypes.last ?? []
        #expect(Set(lastSent) == Set(["myWeight", "myBloodPressure"]))
    }

    @Test("resetForm clears registeredDeviceTypes")
    func resetFormClearsRegisteredDeviceTypes() {
        let (store, _, _, _) = makeSUT()
        store.registeredDeviceTypes = [.weightScale, .bpm]

        store.resetForm()

        #expect(store.registeredDeviceTypes.isEmpty)
    }

    @Test("metric toggle converts weight values and updates validators")
    func metricToggleConvertsWeights() async {
        let (store, _, _, _) = makeSUT()
        store.signupForm.currentWeight.value = "220.0"
        store.signupForm.goalWeight.value = "180.0"

        store.signupForm.useMetric.value = true
        await waitUntil {
            store.signupForm.currentWeight.value != "220.0"
        }

        #expect(store.signupForm.currentWeight.value == "99.8")
        #expect(store.signupForm.goalWeight.value == "81.6")

        store.signupForm.currentWeight.markAsTouched()
        store.signupForm.currentWeight.value = "451"
        #expect(store.signupForm.getError(for: store.signupForm.currentWeight) == SignupStoreTestText.maxWeightKg)
    }
}

// swiftlint:disable large_tuple
@MainActor
private func makeSUT() -> (SignupStore, MockAccountService, MockNotificationHelperService, MockLoggerService) {
    // swiftlint:enable large_tuple
    TestDependencyContainer.reset()

    let accountService = MockAccountService()
    let logger = MockLoggerService()
    let keychain = MockKeychainService()
    let bluetooth = MockBluetoothService()
    let notificationService = MockNotificationHelperService()

    TestDependencyContainer.registerBase(
        logger: logger,
        keychain: keychain,
        bluetooth: bluetooth
    )

    DependencyContainer.shared.register(accountService as AccountServiceProtocol)
    DependencyContainer.shared.register(notificationService as NotificationHelperServiceProtocol)

    notificationService.dismissAllNotifications()
    let store = SignupStore()
    // Prime injector-backed dependencies immediately to keep this store isolated
    // from global container resets in concurrently running suites.
    _ = store.accountService
    _ = store.notificationService
    _ = store.logger
    return (store, accountService, notificationService, logger)
}

@MainActor
private func fillRequiredSignupFields(_ store: SignupStore) {
    store.signupForm.firstName.value = "John"
    store.signupForm.lastName.value = "Doe"
    store.signupForm.email.value = "signup@example.com"
    store.signupForm.password.value = "secret123"
    store.signupForm.confirmPassword.value = "secret123"
    store.signupForm.zipcode.value = "10001"
}

@MainActor
private func stepIndex(_ step: SignupStep, in store: SignupStore) -> Int {
    store.steps.firstIndex(of: step) ?? 0
}

@MainActor
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

private enum SignupStoreTestError: Error {
    case generic
}

private enum SignupStoreTestText {
    static let emailAlreadyInUse = "Email already in use"
    static let maxUsersAlertTitle = "Maximum Users Reached"
    static let maxUsersSwitchingMessage = "Please swipe left to remove any unused accounts before attempting to add a new one."
    static let maxUsersLogInAndRemoveMessage = "Log in to a saved account, then open Settings and tap Switch Accounts to remove users."
    static let errorCreatingAccountTitle = "Error creating account."
    static let emailInUseMessage = "Email address is already in use"
    static let serverErrorMessage = "Unable to reach the Greater Goods servers. The issue is probably on our end. Try again later, but if the problem continues, contact customer service."
    static let somethingWentWrongMessage = "Something went wrong. Please try again. If the problem continues, contact customer service."
    static let maxWeightKg = "value should be less than 450 kg"
}
