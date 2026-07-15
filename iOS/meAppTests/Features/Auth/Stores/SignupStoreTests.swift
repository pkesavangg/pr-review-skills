// Large test fixture file.
// swiftlint:disable file_length
import Foundation
@testable import meApp
import SwiftUI
import Testing

@Suite(.serialized)
@MainActor
// Large cohesive test suite.
// swiftlint:disable:next type_body_length
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
        #expect(store.signupForm.currentWeight.value.isEmpty)
        #expect(store.signupForm.goalWeight.value.isEmpty)
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
        #expect(store.signupForm.firstName.value.isEmpty)
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
        store.selectedDeviceType = .weightScale

        await store.performCreateAccount()
        await store.performSaveDevicesAndFinalize()

        #expect(accountService.signUpCalls == 1)
        #expect(accountService.createGoalCalls == 0)
        #expect(accountService.lastSignUpEmail == "signup@example.com")
        #expect(successCalled == true)
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

        await store.performCreateAccount()

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

        await store.performCreateAccount()
        await store.performSaveDevicesAndFinalize()

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

        await store.performCreateAccount()
        await store.performSaveDevicesAndFinalize()

        #expect(accountService.createGoalCalls == 1)
        #expect(accountService.lastCreatedGoal?.goalType == .maintain)
    }

    @Test("createUser with full device coverage calls completeSignup")
    func createUserNavigatesToSuccessScreen() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.signUpResult = .success(())
        fillRequiredSignupFields(store)
        store.isGoalSkipped = true
        store.signupForm.gender.value = Sex.male.rawValue
        store.registeredDeviceTypes = [.weightScale, .bpm]
        store.selectedDeviceType = .babyScale

        var successCalled = false
        store.onSignupSuccess = { successCalled = true }

        await store.performCreateAccount()
        await store.performSaveDevicesAndFinalize()

        #expect(successCalled == true)
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

        await store.performCreateAccount()
        await store.performSaveDevicesAndFinalize()

        #expect(successCalled == true)
    }

    @Test("createUser completes signup even when the accumulated product-type write fails")
    func createUserCompletesWhenProductTypeWriteFails() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.signUpResult = .success(())
        // A brand-new account's PATCH /account/products can transiently 404 before the
        // account fully propagates. The device profiles saved fine, so signup must still
        // complete — the product types reconcile on the next sync.
        accountService.updateProductTypesResult = .failure(HTTPError.serverError)
        fillRequiredSignupFields(store)
        store.isGoalSkipped = true
        store.selectedDeviceType = .bpm

        var successCalled = false
        store.onSignupSuccess = { successCalled = true }

        await store.performCreateAccount()
        await store.performSaveDevicesAndFinalize()

        #expect(store.currentStep != .signupError)
        #expect(successCalled == true)
        // The productTypes PATCH failure must NOT flip the successfully-saved device.
        let failedCount = store.deviceStatuses.filter {
            if case .failure = $0.status { return true }
            return false
        }.count
        #expect(failedCount == 0)
    }

    @Test("completeSignup calls onSignupSuccess and resets form")
    func completeSignupCallsSuccessAndResets() {
        let (store, _, _, _) = makeSUT()
        var successCalled = false
        store.onSignupSuccess = { successCalled = true }
        store.signupForm.firstName.value = "John"

        store.completeSignup()

        #expect(successCalled == true)
        #expect(store.signupForm.firstName.value.isEmpty)
    }

    @Test("createUser from account switching calls onSignupSuccess immediately via completeSignup")
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

        await store.performCreateAccount()
        await store.performSaveDevicesAndFinalize()

        #expect(successCalled == true)
    }

    @Test("createUser max accounts reached shows alert")
    func createUserMaxAccountsReached() async {
        let (store, accountService, notificationService, _) = makeSUT()
        accountService.signUpResult = .failure(AccountError.maxAccountsReached)
        fillRequiredSignupFields(store)

        await store.performCreateAccount()

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

        await store.performCreateAccount()

        #expect(notificationService.isAlertVisible == true)
        #expect(notificationService.alertData?.title == SignupStoreTestText.maxUsersAlertTitle)
        #expect(notificationService.alertData?.message == SignupStoreTestText.maxUsersSwitchingMessage)
    }

    @Test("createUser badRequest shows email in use toast")
    func createUserBadRequestToast() async {
        let (store, accountService, notificationService, _) = makeSUT()
        accountService.signUpResult = .failure(HTTPError.badRequest)
        fillRequiredSignupFields(store)

        await store.performCreateAccount()

        #expect(notificationService.toastData?.title == SignupStoreTestText.errorCreatingAccountTitle)
        #expect(notificationService.toastData?.message == SignupStoreTestText.emailInUseMessage)
    }

    @Test("createUser no internet shows no toast")
    func createUserNoInternetNoToast() async {
        let (store, accountService, notificationService, _) = makeSUT()
        accountService.signUpResult = .failure(HTTPError.noInternet)
        fillRequiredSignupFields(store)

        await store.performCreateAccount()

        #expect(notificationService.isToastVisible == false)
    }

    @Test("createUser api email in use shows mapped toast")
    func createUserEmailInUseAPIToast() async {
        let (store, accountService, notificationService, _) = makeSUT()
        accountService.signUpResult = .failure(HTTPError.apiError(message: SignupStoreTestText.emailAlreadyInUse, code: 400))
        fillRequiredSignupFields(store)

        await store.performCreateAccount()

        #expect(notificationService.toastData?.message == SignupStoreTestText.emailInUseMessage)
    }

    @Test("createUser server error shows server toast")
    func createUserServerErrorToast() async {
        let (store, accountService, notificationService, _) = makeSUT()
        accountService.signUpResult = .failure(HTTPError.serverError)
        fillRequiredSignupFields(store)

        await store.performCreateAccount()

        #expect(notificationService.toastData?.message == SignupStoreTestText.serverErrorMessage)
    }

    @Test("createUser unknown error shows generic toast")
    func createUserUnknownErrorToast() async {
        let (store, accountService, notificationService, _) = makeSUT()
        accountService.signUpResult = .failure(SignupStoreTestError.generic)
        fillRequiredSignupFields(store)

        await store.performCreateAccount()

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

        await store.performCreateAccount()

        // Per MOB-419 the account is created when COMPLETE is tapped on the password
        // step, then the flow advances to the per-device profile-ready screen.
        #expect(accountService.signUpCalls == 1)
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

        await store.performCreateAccount()

        // Account-creation errors stay as a toast on the password step — they are
        // NOT the per-device error screen.
        #expect(accountService.signUpCalls == 1)
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

        await store.performCreateAccount()
        #expect(accountService.signUpCalls == 1)

        await store.performSaveDevicesAndFinalize()

        // Account was already created at the password step; FINISH only saves products.
        #expect(accountService.signUpCalls == 1)
    }

    @Test("CANCEL on error screen completes signup instead of discarding")
    func cancelOnErrorScreenCompletesSignup() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.signUpResult = .success(())
        // Force a genuine device-profile save failure (goal creation) so the flow lands
        // on the error screen — a productTypes PATCH failure alone no longer blocks signup.
        accountService.createGoalResult = .failure(HTTPError.serverError)
        fillRequiredSignupFields(store)
        store.isGoalSkipped = false
        store.selectedDeviceType = .weightScale
        store.signupForm.goalType.value = GoalType.maintain.rawValue
        store.signupForm.goalWeight.value = "160"
        var successCalled = false
        store.onSignupSuccess = { successCalled = true }

        await store.performCreateAccount()
        await store.performSaveDevicesAndFinalize()
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
        #expect(store.signupForm.firstName.value.isEmpty)
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
        // First loop: baby scale (no sex step). gender defaults to "male", so we
        // clear it to model a user who hasn't picked a sex yet — only then does the
        // subsequent BPM loop surface the .sex step (skipSex is false when gender is empty).
        // Second loop: BPM — sex is collected. Tapping a sex option must keep
        // the user on .sex until they explicitly tap Next.
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.babyScale)
        store.connectAnotherDevice()
        store.signupForm.gender.value = ""
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

    @Test("profileReadyTitle uses per-device copy for the first device, then combined copy once two are done")
    func profileReadyTitlePerDeviceWhileMoreRemain() {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.weightScale)
        #expect(store.profileReadyTitle == SignupStrings.ProfileReadyStep.weightScaleTitle)

        // After the first device is registered, selecting a second device makes two
        // devices "done", so the title switches to the combined multi-device copy.
        store.connectAnotherDevice()
        store.selectDeviceType(.babyScale)
        let expectedNames = [SignupDeviceType.weightScale, .babyScale].map(\.profileReadyName).joined(separator: " & ")
        #expect(store.profileReadyTitle == SignupStrings.ProfileReadyStep.multiDeviceTitle(names: expectedNames))
    }

    @Test("profileReadyTitle shows combined multi-device title when exactly 2 devices done")
    func profileReadyTitleCombinedWhenTwoDevicesDone() {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.weightScale)
        store.connectAnotherDevice()
        store.selectDeviceType(.bpm)

        // Two devices done but a third can still be connected — should show combined title, not all-profiles
        #expect(store.canConnectAnotherDevice == true)
        let expectedNames = [SignupDeviceType.weightScale, .bpm].map(\.profileReadyName).joined(separator: " & ")
        #expect(store.profileReadyTitle == SignupStrings.ProfileReadyStep.multiDeviceTitle(names: expectedNames))
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
        store.currentStepIndex = stepIndex(.password, in: store)

        // Create account first (COMPLETE on password step).
        await store.performCreateAccount()
        #expect(accountService.signUpCalls == 1)
        #expect(store.currentStep == .profileReady)

        // Landing on profileReady must not automatically finalize — FINISH is required.
        try? await Task.sleep(nanoseconds: 50_000_000)
        #expect(accountService.signUpCalls == 1)  // no extra call
        #expect(store.currentStep == .profileReady)

        // Explicit FINISH tap finalizes signup via completeSignup().
        var successCalled = false
        store.onSignupSuccess = { successCalled = true }
        store.finishSignup()
        await waitUntil {
            successCalled || store.currentStep == .signupError
        }
        #expect(successCalled == true)
        #expect(accountService.signUpCalls == 1)  // FINISH does not call signUp again
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

        await store.performCreateAccount()
        await store.performSaveDevicesAndFinalize()

        // The final accumulated write must contain every successful device's
        // product type so `updateProductTypes` (which replaces the array) doesn't
        // leave the account with only the last-written device.
        let lastSent = accountService.allUpdatedProductTypes.last ?? []
        #expect(Set(lastSent) == Set([ProductType.weight.apiValue, ProductType.bloodPressure.apiValue]))
    }

    @Test("resetForm clears registeredDeviceTypes")
    func resetFormClearsRegisteredDeviceTypes() {
        let (store, _, _, _) = makeSUT()
        store.registeredDeviceTypes = [.weightScale, .bpm]

        store.resetForm()

        #expect(store.registeredDeviceTypes.isEmpty)
    }

    // MARK: - isSignupInProgress Gate

    @Test("isSignupInProgress is true during account creation and false after completion")
    func isSignupInProgressGate() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.signUpResult = .success(())
        fillRequiredSignupFields(store)
        store.isGoalSkipped = true
        store.selectedDeviceType = .weightScale

        #expect(accountService.isSignupInProgress == false)
        await store.performCreateAccount()
        // After account creation succeeds, gate stays true until FINISH.
        #expect(accountService.isSignupInProgress == true)

        var successCalled = false
        store.onSignupSuccess = { successCalled = true }
        await store.performSaveDevicesAndFinalize()
        #expect(accountService.isSignupInProgress == false)
        #expect(successCalled == true)
    }

    @Test("isSignupInProgress is false when account creation fails")
    func isSignupInProgressFalseOnFailure() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.signUpResult = .failure(HTTPError.serverError)
        fillRequiredSignupFields(store)
        store.isGoalSkipped = true
        store.selectedDeviceType = .weightScale

        #expect(accountService.isSignupInProgress == false)
        await store.performCreateAccount()
        #expect(accountService.isSignupInProgress == false)
    }

    // MARK: - hasDuplicateBabyName (tested via moveToNextStep)

    @Test("duplicate baby name blocks moveToNextStep and sets error")
    func duplicateBabyNameBlocksNextStep() {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.babyScale)
        guard let addBabyIndex = store.steps.firstIndex(of: .addBaby) else {
            Issue.record("expected .addBaby in steps for babyScale"); return
        }
        store.currentStepIndex = addBabyIndex

        // Add first baby — advances to babyList.
        store.babyProfileForm.name.value = "Alice"
        store.moveToNextStep()

        // Return to addBaby for a second baby with the same name.
        store.addAnotherBaby()
        store.babyProfileForm.name.value = "Alice"
        let stepBefore = store.currentStepIndex
        store.moveToNextStep()

        #expect(store.currentStepIndex == stepBefore)
        #expect(store.babyProfileForm.duplicateNameError != nil)
    }

    @Test("duplicate baby name check is case-insensitive and trims whitespace")
    func duplicateBabyNameCaseInsensitive() {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.babyScale)
        guard let addBabyIndex = store.steps.firstIndex(of: .addBaby) else {
            Issue.record("expected .addBaby in steps for babyScale"); return
        }
        store.currentStepIndex = addBabyIndex

        store.babyProfileForm.name.value = "Alice"
        store.moveToNextStep()

        store.addAnotherBaby()
        store.babyProfileForm.name.value = " ALICE "
        let stepBefore = store.currentStepIndex
        store.moveToNextStep()

        #expect(store.currentStepIndex == stepBefore)
        #expect(store.babyProfileForm.duplicateNameError != nil)
    }

    @Test("no duplicate error when baby list is empty")
    func noDuplicateWhenNoBabies() {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.babyScale)
        guard let addBabyIndex = store.steps.firstIndex(of: .addBaby) else {
            Issue.record("expected .addBaby in steps for babyScale"); return
        }
        store.currentStepIndex = addBabyIndex

        store.babyProfileForm.name.value = "Alice"
        let stepBefore = store.currentStepIndex
        store.moveToNextStep()

        #expect(store.currentStepIndex != stepBefore)
        #expect(store.babyProfileForm.duplicateNameError == nil)
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

    // MARK: - Baby Management

    @Test("editBaby loads baby data into form and navigates to addBaby step")
    func editBabyLoadsFormAndNavigates() {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.babyScale)
        guard let addBabyIndex = store.steps.firstIndex(of: .addBaby) else {
            Issue.record("expected .addBaby in steps for babyScale"); return
        }
        store.currentStepIndex = addBabyIndex
        store.babyProfileForm.name.value = "Alice"
        store.moveToNextStep()
        #expect(store.babies.count == 1)

        store.editBaby(at: 0)

        #expect(store.babyProfileForm.name.value == "Alice")
        #expect(store.isEditingBabyIndex == 0)
        #expect(store.currentStep == .addBaby)
    }

    @Test("editBaby with out-of-bounds index is a no-op")
    func editBabyOutOfBoundsIsNoOp() {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.babyScale)

        store.editBaby(at: 99)

        #expect(store.isEditingBabyIndex == nil)
    }

    @Test("deleteBaby removes the baby at the given index")
    func deleteBabyRemovesBabyAtIndex() {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.babyScale)
        guard let addBabyIndex = store.steps.firstIndex(of: .addBaby) else {
            Issue.record("expected .addBaby in steps for babyScale"); return
        }
        store.currentStepIndex = addBabyIndex
        store.babyProfileForm.name.value = "Alice"
        store.moveToNextStep()
        #expect(store.babies.count == 1)

        store.deleteBaby(at: 0)

        #expect(store.babies.isEmpty)
    }

    @Test("deleteBaby with out-of-bounds index is a no-op")
    func deleteBabyOutOfBoundsIsNoOp() {
        let (store, _, _, _) = makeSUT()

        store.deleteBaby(at: 0)

        #expect(store.babies.isEmpty)
    }

    @Test("confirmDeleteBaby shows delete confirmation alert")
    func confirmDeleteBabyShowsAlert() {
        let (store, _, notificationService, _) = makeSUT()
        store.selectDeviceType(.babyScale)
        guard let addBabyIndex = store.steps.firstIndex(of: .addBaby) else {
            Issue.record("expected .addBaby in steps for babyScale"); return
        }
        store.currentStepIndex = addBabyIndex
        store.babyProfileForm.name.value = "Alice"
        store.moveToNextStep()

        store.confirmDeleteBaby(at: 0)

        #expect(notificationService.isAlertVisible == true)
        #expect(notificationService.alertData?.buttons.count == 2)
    }

    @Test("confirmDeleteBaby primary button removes the baby")
    func confirmDeleteBabyPrimaryButtonDeletesBaby() {
        let (store, _, notificationService, _) = makeSUT()
        store.selectDeviceType(.babyScale)
        guard let addBabyIndex = store.steps.firstIndex(of: .addBaby) else {
            Issue.record("expected .addBaby in steps for babyScale"); return
        }
        store.currentStepIndex = addBabyIndex
        store.babyProfileForm.name.value = "Alice"
        store.moveToNextStep()
        #expect(store.babies.count == 1)

        store.confirmDeleteBaby(at: 0)
        notificationService.alertData?.buttons.first?.action(nil)

        #expect(store.babies.isEmpty)
    }

    @Test("handleSkip on addBaby with no editing shows skip-baby alert")
    func handleSkipOnAddBabyShowsSkipAlert() {
        let (store, _, notificationService, _) = makeSUT()
        store.selectDeviceType(.babyScale)
        guard let addBabyIndex = store.steps.firstIndex(of: .addBaby) else {
            Issue.record("expected .addBaby in steps for babyScale"); return
        }
        store.currentStepIndex = addBabyIndex

        store.handleSkip()

        #expect(notificationService.isAlertVisible == true)
        #expect(notificationService.alertData?.buttons.count == 2)
    }

    @Test("skip-baby alert primary action jumps to password and resets baby form")
    func skipAddBabyAlertPrimaryJumpsToPassword() {
        let (store, _, notificationService, _) = makeSUT()
        store.selectDeviceType(.babyScale)
        guard let addBabyIndex = store.steps.firstIndex(of: .addBaby) else {
            Issue.record("expected .addBaby in steps for babyScale"); return
        }
        store.currentStepIndex = addBabyIndex
        store.babyProfileForm.name.value = "Charlie"

        store.handleSkip()
        notificationService.alertData?.buttons.first?.action(nil)

        let expectedStep: SignupStep = store.steps.contains(.password) ? .password : .profileReady
        #expect(store.currentStep == expectedStep)
        #expect(store.babyProfileForm.name.value.isEmpty)
    }

    @Test("handleSkip on addBaby while editing shows edit-skip confirmation alert")
    func handleSkipOnAddBabyWhileEditingShowsAlert() {
        let (store, _, notificationService, _) = makeSUT()
        store.selectDeviceType(.babyScale)
        guard let addBabyIndex = store.steps.firstIndex(of: .addBaby) else {
            Issue.record("expected .addBaby in steps for babyScale"); return
        }
        store.currentStepIndex = addBabyIndex
        store.babyProfileForm.name.value = "Alice"
        store.moveToNextStep()
        store.editBaby(at: 0)

        store.handleSkip()

        #expect(notificationService.isAlertVisible == true)
    }

    @Test("skip-edit-baby alert primary action navigates to babyList and clears editing index")
    func skipEditBabyAlertPrimaryGoesToBabyList() {
        let (store, _, notificationService, _) = makeSUT()
        store.selectDeviceType(.babyScale)
        guard let addBabyIndex = store.steps.firstIndex(of: .addBaby) else {
            Issue.record("expected .addBaby in steps for babyScale"); return
        }
        store.currentStepIndex = addBabyIndex
        store.babyProfileForm.name.value = "Alice"
        store.moveToNextStep()
        store.editBaby(at: 0)
        store.handleSkip()

        notificationService.alertData?.buttons.first?.action(nil)

        #expect(store.currentStep == .babyList)
        #expect(store.isEditingBabyIndex == nil)
        #expect(store.babyProfileForm.name.value.isEmpty)
    }

    @Test("moveToPreviousStep skips babyList back to addBaby when no babies saved")
    func moveToPreviousStepSkipsBabyListWhenEmpty() {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.babyScale)
        guard let babyListIndex = store.steps.firstIndex(of: .babyList) else {
            Issue.record("expected .babyList in steps for babyScale"); return
        }
        store.currentStepIndex = babyListIndex

        store.moveToPreviousStep()

        #expect(store.currentStep == .addBaby)
    }

    @Test("next button enabled on babyList when babies list is non-empty")
    func nextEnabledBabyListNonEmpty() {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.babyScale)
        guard let addBabyIndex = store.steps.firstIndex(of: .addBaby) else {
            Issue.record("expected .addBaby in steps"); return
        }
        store.currentStepIndex = addBabyIndex
        store.babyProfileForm.name.value = "Alice"
        store.moveToNextStep()

        #expect(store.currentStep == .babyList)
        #expect(store.isNextEnabled == true)
    }

    @Test("next button disabled on babyList when babies list is empty")
    func nextDisabledBabyListEmpty() {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.babyScale)
        guard let babyListIndex = store.steps.firstIndex(of: .babyList) else {
            Issue.record("expected .babyList in steps"); return
        }
        store.currentStepIndex = babyListIndex
        store.updateNextButtonState()

        #expect(store.isNextEnabled == false)
    }

    @Test("performSaveDevicesAndFinalize with babyScale saves baby and completes signup")
    func finalizeWithBabyScaleSavesBabyAndCompletes() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.signUpResult = .success(())
        fillRequiredSignupFields(store)
        store.isGoalSkipped = true
        store.selectDeviceType(.babyScale)

        guard let addBabyIndex = store.steps.firstIndex(of: .addBaby) else {
            Issue.record("expected .addBaby in steps"); return
        }
        store.currentStepIndex = addBabyIndex
        store.babyProfileForm.name.value = "Charlie"
        store.moveToNextStep()
        #expect(store.babies.count == 1)

        var successCalled = false
        store.onSignupSuccess = { successCalled = true }

        await store.performCreateAccount()
        await store.performSaveDevicesAndFinalize()

        #expect(successCalled == true)
        let anyFailed = store.deviceStatuses.contains {
            if case .failure = $0.status { return true }; return false
        }
        #expect(anyFailed == false)
    }

    // MARK: - Cancel Signup

    @Test("cancelSignup with router navigates back and resets form")
    func cancelSignupWithRouterNavigatesBack() {
        let (store, _, _, _) = makeSUT()
        let router = Router<AuthRoute>()
        router.navigate(to: .signup)
        store.signupForm.firstName.value = "John"

        store.cancelSignup(router: router)

        #expect(router.stack.isEmpty)
        #expect(store.signupForm.firstName.value.isEmpty)
    }

    @Test("cancelSignup from account switching calls dismissAction and resets form")
    func cancelSignupFromAccountSwitchingCallsDismissAction() {
        let (store, _, _, _) = makeSUT()
        store.isFromAccountSwitching = true
        var dismissed = false
        store.dismissAction = { dismissed = true }

        store.cancelSignup()

        #expect(dismissed == true)
        #expect(store.signupForm.firstName.value.isEmpty)
    }

    // MARK: - handleExit extra paths

    @Test("handleExit on profileReady marks signup not in progress and resets without alert")
    func handleExitOnProfileReadyResetsWithoutAlert() {
        let (store, accountService, notificationService, _) = makeSUT()
        store.currentStepIndex = stepIndex(.profileReady, in: store)
        store.signupForm.firstName.value = "John"
        accountService.isSignupInProgress = true

        store.handleExit()

        #expect(notificationService.isAlertVisible == false)
        #expect(accountService.isSignupInProgress == false)
        #expect(store.signupForm.firstName.value.isEmpty)
    }

    @Test("handleExit from account switching with pristine form calls dismissAction without alert")
    func handleExitFromAccountSwitchingPristineCallsDismissAction() {
        let (store, _, notificationService, _) = makeSUT()
        store.isFromAccountSwitching = true
        var dismissed = false
        store.dismissAction = { dismissed = true }

        store.handleExit()

        #expect(notificationService.isAlertVisible == false)
        #expect(dismissed == true)
    }

    @Test("handleExit from account switching with dirty form — primary button calls dismissAction")
    func handleExitFromAccountSwitchingDirtyFormPrimaryCallsDismissAction() {
        let (store, _, notificationService, _) = makeSUT()
        store.isFromAccountSwitching = true
        store.signupForm.firstName.value = "John"
        var dismissed = false
        store.dismissAction = { dismissed = true }

        store.handleExit()
        notificationService.alertData?.buttons.first?.action(nil)

        #expect(dismissed == true)
    }

    // MARK: - completeSignup isFromAccountSwitching path

    @Test("completeSignup from account switching with no onSignupSuccess uses dismissAction")
    func completeSignupFromAccountSwitchingUsesDismissAction() {
        let (store, _, _, _) = makeSUT()
        store.isFromAccountSwitching = true
        store.onSignupSuccess = nil
        var dismissed = false
        store.dismissAction = { dismissed = true }

        store.completeSignup()

        #expect(dismissed == true)
        #expect(store.signupForm.firstName.value.isEmpty)
    }

    // MARK: - Retry Failed Devices

    @Test("retryFailedDevices succeeds after a device-save failure and advances to allProfilesReady")
    func retryFailedDevicesAdvancesToAllProfilesReady() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.signUpResult = .success(())
        fillRequiredSignupFields(store)
        // Land on the error screen via a genuine device-profile save failure (goal creation);
        // a productTypes PATCH failure alone no longer blocks signup.
        store.isGoalSkipped = false
        store.selectedDeviceType = .weightScale
        store.signupForm.goalType.value = GoalType.maintain.rawValue
        store.signupForm.goalWeight.value = "160"

        accountService.createGoalResult = .failure(HTTPError.serverError)
        await store.performCreateAccount()
        await store.performSaveDevicesAndFinalize()
        #expect(store.currentStep == .signupError)

        accountService.createGoalResult = .success(())
        store.retryFailedDevices()
        // Wait for the retry to actually finish. The store starts on .signupError, so
        // we must NOT treat .signupError as a terminal wait condition (it would return
        // immediately, before the async retry runs). The success path lands on .allProfilesReady.
        await waitUntil { store.currentStep == .allProfilesReady }

        let anyFailed = store.deviceStatuses.contains {
            if case .failure = $0.status { return true }; return false
        }
        #expect(anyFailed == false)
        #expect(store.currentStep == .allProfilesReady)
    }

    // MARK: - Computed Properties

    @Test("pickNextDeviceTitle shows the last-completed device title for a single registered device")
    func pickNextDeviceTitleSingleDevice() {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.weightScale)
        store.connectAnotherDevice()

        #expect(store.lastCompletedDeviceType == .weightScale)
        #expect(store.pickNextDeviceTitle.isEmpty == false)
    }

    @Test("pickNextDeviceTitle shows combined title when 2 or more devices are registered")
    func pickNextDeviceTitleMultipleDevices() {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.weightScale)
        store.connectAnotherDevice()
        store.selectDeviceType(.bpm)
        store.connectAnotherDevice()

        #expect(store.registeredDeviceTypes.count == 2)
        #expect(store.pickNextDeviceTitle.contains("&"))
    }

    @Test("allCompletedDevices includes current selectedDeviceType when not yet in registeredDeviceTypes")
    func allCompletedDevicesIncludesCurrentDevice() {
        let (store, _, _, _) = makeSUT()
        store.registeredDeviceTypes = [.weightScale]
        store.selectedDeviceType = .bpm

        let completed = store.allCompletedDevices

        #expect(completed.contains(.weightScale))
        #expect(completed.contains(.bpm))
        #expect(completed.count == 2)
    }

    @Test("allCompletedDevices does not duplicate currentDevice when already in registeredDeviceTypes")
    func allCompletedDevicesNoDuplicateWhenAlreadyRegistered() {
        let (store, _, _, _) = makeSUT()
        store.registeredDeviceTypes = [.weightScale]
        store.selectedDeviceType = .weightScale

        let completed = store.allCompletedDevices

        #expect(completed.count == 1)
    }

    @Test("allCompletedDevices returns registeredDeviceTypes only when selectedDeviceType is nil")
    func allCompletedDevicesWithNilSelection() {
        let (store, _, _, _) = makeSUT()
        store.registeredDeviceTypes = [.bpm]
        store.selectedDeviceType = nil

        let completed = store.allCompletedDevices

        #expect(completed == [.bpm])
    }

    @Test("progressValue returns the snapshot value (0.9) on pickNextDevice step")
    func progressValueOnPickNextDeviceReturnsSavedValue() {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.weightScale)
        store.connectAnotherDevice()

        #expect(store.currentStep == .pickNextDevice)
        #expect(store.progressValue == 0.9)
    }

    // MARK: - updateNextButtonState uncovered steps

    @Test("next button enabled on pickNextDevice when a device type is selected")
    func nextEnabledPickNextDeviceWithSelection() {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.weightScale)
        store.connectAnotherDevice()
        store.selectedDeviceType = .bpm
        store.updateNextButtonState()

        #expect(store.isNextEnabled == true)
    }

    @Test("next button disabled on pickNextDevice when no device type is selected")
    func nextDisabledPickNextDeviceWithoutSelection() {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.weightScale)
        store.connectAnotherDevice()
        store.selectedDeviceType = nil
        store.updateNextButtonState()

        #expect(store.isNextEnabled == false)
    }

    // MARK: - createAccount guard

    @Test("createAccount is a no-op when isSignupInProgress is already true")
    func createAccountSkipsWhenAlreadyInProgress() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.isSignupInProgress = true

        store.createAccount()
        try? await Task.sleep(nanoseconds: 50_000_000)

        #expect(accountService.signUpCalls == 0)
    }

    // MARK: - touchAndValidate additional fields

    @Test("touchAndValidate marks firstName as touched")
    func touchAndValidateFirstNameMarkedTouched() {
        let (store, _, _, _) = makeSUT()

        #expect(store.signupForm.firstName.isTouched == false)
        store.touchAndValidate(field: .firstName)
        #expect(store.signupForm.firstName.isTouched == true)
    }

    @Test("touchAndValidate marks lastName as touched")
    func touchAndValidateLastNameMarkedTouched() {
        let (store, _, _, _) = makeSUT()

        #expect(store.signupForm.lastName.isTouched == false)
        store.touchAndValidate(field: .lastName)
        #expect(store.signupForm.lastName.isTouched == true)
    }

    @Test("touchAndValidate marks password as touched and triggers form-level validation")
    func touchAndValidatePasswordTriggersValidation() {
        let (store, _, _, _) = makeSUT()
        store.signupForm.password.value = "secret"
        store.signupForm.confirmPassword.value = "different"

        store.touchAndValidate(field: .password)

        #expect(store.signupForm.password.isTouched == true)
        #expect(store.signupForm.formErrors[.passwordMatch] == true)
    }

    @Test("touchAndValidate marks confirmPassword as touched and triggers form-level validation")
    func touchAndValidateConfirmPasswordTriggersValidation() {
        let (store, _, _, _) = makeSUT()
        store.signupForm.password.value = "secret"
        store.signupForm.confirmPassword.value = "different"

        store.touchAndValidate(field: .confirmPassword)

        #expect(store.signupForm.confirmPassword.isTouched == true)
        #expect(store.signupForm.formErrors[.passwordMatch] == true)
    }

    @Test("touchAndValidate marks currentWeight as touched")
    func touchAndValidateCurrentWeightMarkedTouched() {
        let (store, _, _, _) = makeSUT()

        store.touchAndValidate(field: .currentWeight)

        #expect(store.signupForm.currentWeight.isTouched == true)
    }

    @Test("touchAndValidate marks goalWeight as touched")
    func touchAndValidateGoalWeightMarkedTouched() {
        let (store, _, _, _) = makeSUT()

        store.touchAndValidate(field: .goalWeight)

        #expect(store.signupForm.goalWeight.isTouched == true)
    }

    @Test("touchAndValidate with an unhandled field hits the default branch without crashing")
    func touchAndValidateUnhandledFieldIsNoOp() {
        let (store, _, _, _) = makeSUT()

        store.touchAndValidate(field: .bmi)

        #expect(store.signupForm.firstName.isTouched == false)
        #expect(store.signupForm.email.isTouched == false)
    }

    // MARK: - handleSkip default case

    @Test("handleSkip on a step with no special handler advances via moveToNextStep")
    func handleSkipDefaultCaseAdvancesStep() {
        let (store, _, _, _) = makeSUT()
        store.currentStepIndex = stepIndex(.email, in: store)
        let emailIndex = store.currentStepIndex

        store.handleSkip()

        #expect(store.currentStepIndex > emailIndex)
    }

    // MARK: - updateHeightPickerValues

    @Test("updateHeightPickerValues populates selectedHeightCm and selectedHeightInches from stored height")
    func updateHeightPickerValuesSetsPickerSelections() {
        let (store, _, _, _) = makeSUT()
        let storedHeight = ConversionTools.convertCmToStoredHeight(170)

        store.updateHeightPickerValues(from: storedHeight)

        #expect(store.selectedHeightCm.isEmpty == false)
        #expect(store.selectedHeightInches.isEmpty == false)
    }

    // MARK: - Add Baby form pristine logic

    @Test("re-entering add baby for a new baby resets the populated form")
    func reenteringAddBabyForNewBabyResetsForm() throws {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.babyScale)

        // Simulate stale form state left over from a prior interaction.
        store.babyProfileForm.name.value = "Olivia"
        store.isEditingBabyIndex = nil

        let addBabyIndex = store.steps.firstIndex(of: .addBaby)
        #expect(addBabyIndex != nil)
        store.currentStepIndex = try #require(addBabyIndex)

        // New baby (not an edit) must start from a clean form so no phantom
        // "Required." error shows for the blurred-but-empty name field.
        #expect(store.babyProfileForm.name.value.isEmpty)
    }

    @Test("re-entering add baby while editing keeps the populated form")
    func reenteringAddBabyWhileEditingKeepsForm() throws {
        let (store, _, _, _) = makeSUT()
        store.selectDeviceType(.babyScale)

        store.babyProfileForm.name.value = "Olivia"
        store.isEditingBabyIndex = 0

        let addBabyIndex = store.steps.firstIndex(of: .addBaby)
        #expect(addBabyIndex != nil)
        store.currentStepIndex = try #require(addBabyIndex)

        // Edits keep their populated values — the reset only fires for new babies.
        #expect(store.babyProfileForm.name.value == "Olivia")
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
    static let serverErrorMessage = "Unable to reach the Greater Goods servers. The issue is probably on our end. " +
        "Try again later, but if the problem continues, contact customer service."
    static let somethingWentWrongMessage = "Something went wrong. Please try again. If the problem continues, contact customer service."
    static let maxWeightKg = "value should be less than 450 kg"
}
// swiftlint:enable file_length
