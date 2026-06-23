//
//  SignupStoreTests.swift
//  meAppTests
//
//  Comprehensive unit tests for SignupStore.
//

import Testing
import Foundation
@testable import meApp

@Suite(.serialized)
@MainActor
struct SignupStoreTests {

    // MARK: - makeSUT

    private func makeSUT(
        signUpResult: Account? = nil,
        signUpError: Error? = nil,
        createGoalError: Error? = nil
    ) -> (SignupStore, MockAccountService, MockNotificationHelperService, MockLoggerService) {
        let accountService = MockAccountService()
        accountService.signUpResult = signUpResult ?? AccountTestFixtures.makeAccount()
        accountService.signUpError = signUpError
        accountService.createGoalError = createGoalError

        let notificationService = MockNotificationHelperService()
        let logger = MockLoggerService()

        DependencyContainer.shared.register(accountService as AccountServiceProtocol)
        DependencyContainer.shared.register(notificationService as NotificationHelperService)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)

        let store = SignupStore()
        // Force lazy @Injector resolution NOW so all services resolve the mocks we just registered.
        _ = store.accountService
        _ = store.notificationService
        _ = store.logger

        return (store, accountService, notificationService, logger)
    }

    // MARK: - waitUntil helper

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

    // MARK: - Initial state

    @Test("Initial step is .name")
    func initialStepIsName() {
        let (store, _, _, _) = makeSUT()
        #expect(store.currentStep == .name)
        #expect(store.currentStepIndex == SignupStep.name.index)
    }

    @Test("Initial step index is 0")
    func initialStepIndexIsZero() {
        let (store, _, _, _) = makeSUT()
        #expect(store.currentStepIndex == 0)
    }

    @Test("isNextEnabled is false on init")
    func isNextEnabledFalseOnInit() {
        let (store, _, _, _) = makeSUT()
        #expect(!store.isNextEnabled)
    }

    @Test("isGoalSkipped is false on init")
    func isGoalSkippedFalseOnInit() {
        let (store, _, _, _) = makeSUT()
        #expect(!store.isGoalSkipped)
    }

    @Test("steps array has 7 steps")
    func stepsHasSevenItems() {
        let (store, _, _, _) = makeSUT()
        #expect(store.steps.count == 7)
    }

    // MARK: - Navigation

    @Test("moveToNextStep advances step from name to dateOfBirth")
    func moveToNextStepAdvances() {
        let (store, _, _, _) = makeSUT()
        store.moveToNextStep()
        #expect(store.currentStep == .dateOfBirth)
    }

    @Test("moveToPreviousStep does nothing when on first step")
    func moveToPreviousStepDoesNothingOnFirst() {
        let (store, _, _, _) = makeSUT()
        store.moveToPreviousStep()
        #expect(store.currentStepIndex == 0)
        #expect(store.currentStep == .name)
    }

    @Test("moveToPreviousStep goes back from dateOfBirth to name")
    func moveToPreviousStepGoesBack() {
        let (store, _, _, _) = makeSUT()
        store.currentStepIndex = SignupStep.dateOfBirth.index
        store.moveToPreviousStep()
        #expect(store.currentStep == .name)
    }

    @Test("moveToPreviousStep resets isGoalSkipped when backing to goal step")
    func moveToPreviousStepResetsGoalSkip() {
        let (store, _, _, _) = makeSUT()
        store.isGoalSkipped = true
        store.currentStepIndex = SignupStep.email.index
        store.moveToPreviousStep()
        #expect(store.currentStep == .goal)
        #expect(!store.isGoalSkipped)
    }

    @Test("handleSkip sets isGoalSkipped and advances to email")
    func handleSkipSetsGoalSkippedAndAdvances() {
        let (store, _, _, _) = makeSUT()
        store.currentStepIndex = SignupStep.goal.index
        store.handleSkip()
        #expect(store.isGoalSkipped)
        #expect(store.currentStep == .email)
    }

    // MARK: - progressValue

    @Test("progressValue is 1/7 on first step")
    func progressValueFirstStep() {
        let (store, _, _, _) = makeSUT()
        let expected = 1.0 / 7.0
        #expect(abs(store.progressValue - expected) < 0.0001)
    }

    @Test("progressValue is 7/7 on last step")
    func progressValueLastStep() {
        let (store, _, _, _) = makeSUT()
        store.currentStepIndex = 6
        #expect(abs(store.progressValue - 1.0) < 0.0001)
    }

    // MARK: - updateNextButtonState

    @Test("isNextEnabled true on name step when both names are valid")
    func isNextEnabledNameStepValid() {
        let (store, _, _, _) = makeSUT()
        store.currentStepIndex = SignupStep.name.index
        store.signupForm.firstName.value = "Alice"
        store.signupForm.firstName.validate()
        store.signupForm.lastName.value = "Smith"
        store.signupForm.lastName.validate()
        store.updateNextButtonState()
        #expect(store.isNextEnabled)
    }

    @Test("isNextEnabled false on name step when firstName empty")
    func isNextEnabledFalseOnNameStepWithoutName() {
        let (store, _, _, _) = makeSUT()
        store.currentStepIndex = SignupStep.name.index
        store.signupForm.firstName.value = ""
        store.signupForm.firstName.validate()
        store.signupForm.lastName.value = "Smith"
        store.signupForm.lastName.validate()
        store.updateNextButtonState()
        #expect(!store.isNextEnabled)
    }

    @Test("isNextEnabled true on email step with valid email")
    func isNextEnabledEmailStep() {
        let (store, _, _, _) = makeSUT()
        store.currentStepIndex = SignupStep.email.index
        store.signupForm.email.value = "test@example.com"
        store.signupForm.email.validate()
        store.updateNextButtonState()
        #expect(store.isNextEnabled)
    }

    @Test("isNextEnabled true on password step when all fields valid and passwords match")
    func isNextEnabledPasswordStepAllValid() {
        let (store, _, _, _) = makeSUT()
        store.currentStepIndex = SignupStep.password.index
        store.signupForm.password.value = "secure123"
        store.signupForm.password.validate()
        store.signupForm.confirmPassword.value = "secure123"
        store.signupForm.confirmPassword.validate()
        store.signupForm.zipcode.value = "90210"
        store.signupForm.zipcode.validate()
        store.signupForm.validate()
        store.updateNextButtonState()
        #expect(store.isNextEnabled)
    }

    @Test("isNextEnabled false on password step when passwords do not match")
    func isNextEnabledFalsePasswordMismatch() {
        let (store, _, _, _) = makeSUT()
        store.currentStepIndex = SignupStep.password.index
        store.signupForm.password.value = "secure123"
        store.signupForm.password.validate()
        store.signupForm.confirmPassword.value = "different"
        store.signupForm.confirmPassword.validate()
        store.signupForm.zipcode.value = "90210"
        store.signupForm.zipcode.validate()
        store.signupForm.validate()
        store.updateNextButtonState()
        #expect(!store.isNextEnabled)
    }

    // MARK: - createUser: success

    @Test("createUser calls accountService.signUp with correct credentials")
    func createUserCallsSignUp() async {
        let (store, accountService, _, _) = makeSUT()
        store.signupForm.email.value = "test@example.com"
        store.signupForm.password.value = "pass123"
        store.isGoalSkipped = true

        await store.createUser()

        #expect(accountService.signUpCallCount == 1)
        #expect(accountService.lastSignUpEmail == "test@example.com")
        #expect(accountService.lastSignUpPassword == "pass123")
    }

    @Test("createUser calls createGoal when goal not skipped")
    func createUserCallsCreateGoal() async {
        let (store, accountService, _, _) = makeSUT()
        store.signupForm.email.value = "test@example.com"
        store.signupForm.password.value = "pass123"
        // Set up a valid goal (lose mode, different weights)
        store.isGoalSkipped = false
        store.signupForm.goalType.value = GoalTypeSegment.losegainValue
        store.signupForm.currentWeight.value = "200"
        store.signupForm.goalWeight.value = "180"

        await store.createUser()

        #expect(accountService.createGoalCallCount == 1)
    }

    @Test("createUser does not call createGoal when goal is skipped")
    func createUserSkipsGoalWhenFlagSet() async {
        let (store, accountService, _, _) = makeSUT()
        store.signupForm.email.value = "test@example.com"
        store.signupForm.password.value = "pass123"
        store.isGoalSkipped = true

        await store.createUser()

        #expect(accountService.createGoalCallCount == 0)
    }

    @Test("createUser dismisses loader after success")
    func createUserDismissesLoaderOnSuccess() async {
        let (store, _, notificationService, _) = makeSUT()
        store.signupForm.email.value = "test@example.com"
        store.signupForm.password.value = "pass123"
        store.isGoalSkipped = true

        await store.createUser()

        #expect(notificationService.dismissLoaderCallCount >= 1)
        #expect(notificationService.loaderData == nil)
    }

    @Test("createUser shows loader before attempting signup")
    func createUserShowsLoader() async {
        let (store, _, notificationService, _) = makeSUT()
        store.signupForm.email.value = "test@example.com"
        store.signupForm.password.value = "pass"
        store.isGoalSkipped = true

        await store.createUser()

        #expect(notificationService.showLoaderCallCount >= 1)
    }

    @Test("createUser calls onSignupSuccess on success when not from account switching")
    func createUserCallsOnSignupSuccess() async {
        let (store, _, _, _) = makeSUT()
        var successCalled = false
        store.onSignupSuccess = { successCalled = true }
        store.isFromAccountSwitching = false
        store.signupForm.email.value = "test@example.com"
        store.signupForm.password.value = "pass"
        store.isGoalSkipped = true

        await store.createUser()

        #expect(successCalled)
    }

    @Test("createUser logs signup started")
    func createUserLogsSignupStarted() async {
        let (store, _, _, logger) = makeSUT()
        store.signupForm.email.value = "test@example.com"
        store.signupForm.password.value = "pass"
        store.isGoalSkipped = true

        await store.createUser()

        #expect(logger.hasLog(level: .info, containing: "Signup flow started"))
    }

    @Test("createUser logs signup succeeded")
    func createUserLogsSignupSucceeded() async {
        let (store, _, _, logger) = makeSUT()
        store.signupForm.email.value = "test@example.com"
        store.signupForm.password.value = "pass"
        store.isGoalSkipped = true

        await store.createUser()

        #expect(logger.hasLog(level: .success, containing: "Signup flow succeeded"))
    }

    // MARK: - createUser: error handling

    @Test("createUser shows emailInUse toast on badRequest error")
    func createUserShowsEmailInUseOnBadRequest() async {
        let (store, _, notificationService, _) = makeSUT(signUpError: HTTPError.badRequest)
        store.signupForm.email.value = "test@example.com"
        store.signupForm.password.value = "pass"
        store.isGoalSkipped = true

        await store.createUser()

        #expect(notificationService.showToastCallCount == 1)
        #expect(notificationService.lastShownToast?.message == ToastStrings.emailInUse)
    }

    @Test("createUser shows no toast on noInternet error")
    func createUserShowsNoToastOnNoInternet() async {
        let (store, _, notificationService, _) = makeSUT(signUpError: HTTPError.noInternet)
        store.signupForm.email.value = "test@example.com"
        store.signupForm.password.value = "pass"
        store.isGoalSkipped = true

        await store.createUser()

        // noInternet: no toast displayed (handled by NetworkMonitor)
        #expect(notificationService.showToastCallCount == 0)
    }

    @Test("createUser shows serverError toast on serverError")
    func createUserShowsServerErrorToast() async {
        let (store, _, notificationService, _) = makeSUT(signUpError: HTTPError.serverError)
        store.signupForm.email.value = "test@example.com"
        store.signupForm.password.value = "pass"
        store.isGoalSkipped = true

        await store.createUser()

        #expect(notificationService.showToastCallCount == 1)
        #expect(notificationService.lastShownToast?.message == ToastStrings.serverError)
    }

    @Test("createUser shows emailInUse toast on apiError with emailAlreadyInUse message")
    func createUserShowsEmailInUseOnApiError() async {
        let err = HTTPError.apiError(message: CommonStrings.emailAlreadyInUse, code: 400)
        let (store, _, notificationService, _) = makeSUT(signUpError: err)
        store.signupForm.email.value = "test@example.com"
        store.signupForm.password.value = "pass"
        store.isGoalSkipped = true

        await store.createUser()

        #expect(notificationService.showToastCallCount == 1)
        #expect(notificationService.lastShownToast?.message == ToastStrings.emailInUse)
    }

    @Test("createUser shows somethingWentWrong toast on unknown error")
    func createUserShowsSomethingWentWrongOnUnknownError() async {
        let err = NSError(domain: "Test", code: 99)
        let (store, _, notificationService, _) = makeSUT(signUpError: err)
        store.signupForm.email.value = "test@example.com"
        store.signupForm.password.value = "pass"
        store.isGoalSkipped = true

        await store.createUser()

        #expect(notificationService.showToastCallCount == 1)
        #expect(notificationService.lastShownToast?.message == ToastStrings.somethingWentWrong)
    }

    @Test("createUser shows maxAccounts alert on maxAccountsReached error")
    func createUserShowsMaxAccountsAlert() async {
        let (store, _, notificationService, _) = makeSUT(signUpError: AccountError.maxAccountsReached)
        store.signupForm.email.value = "test@example.com"
        store.signupForm.password.value = "pass"
        store.isGoalSkipped = true

        await store.createUser()

        #expect(notificationService.showAlertCallCount == 1)
        #expect(notificationService.showToastCallCount == 0)
    }

    @Test("createUser logs error on signup failure")
    func createUserLogsErrorOnFailure() async {
        let (store, _, _, logger) = makeSUT(signUpError: HTTPError.serverError)
        store.signupForm.email.value = "test@example.com"
        store.signupForm.password.value = "pass"
        store.isGoalSkipped = true

        await store.createUser()

        #expect(logger.hasLog(level: .error, containing: "Signup flow failed"))
    }

    @Test("createUser dismisses loader even on failure")
    func createUserDismissesLoaderOnFailure() async {
        let (store, _, notificationService, _) = makeSUT(signUpError: HTTPError.serverError)
        store.signupForm.email.value = "test@example.com"
        store.signupForm.password.value = "pass"
        store.isGoalSkipped = true

        await store.createUser()

        #expect(notificationService.dismissLoaderCallCount >= 1)
        #expect(notificationService.loaderData == nil)
    }

    // MARK: - touchAndValidate

    @Test("touchAndValidate marks firstName touched")
    func touchAndValidateFirstName() {
        let (store, _, _, _) = makeSUT()
        store.touchAndValidate(field: .firstName)
        #expect(store.signupForm.firstName.isTouched)
    }

    @Test("touchAndValidate marks email touched")
    func touchAndValidateEmail() {
        let (store, _, _, _) = makeSUT()
        store.touchAndValidate(field: .email)
        #expect(store.signupForm.email.isTouched)
    }

    @Test("touchAndValidate for password triggers form validation")
    func touchAndValidatePasswordTriggersFormValidation() {
        let (store, _, _, _) = makeSUT()
        store.signupForm.password.value = "abc123"
        store.signupForm.confirmPassword.value = "xyz789"
        store.touchAndValidate(field: .password)
        #expect(store.signupForm.formErrors[.passwordMatch])
    }

    // MARK: - resetForm

    @Test("resetForm resets step to name")
    func resetFormResetsStep() {
        let (store, _, _, _) = makeSUT()
        store.currentStepIndex = 4
        store.resetForm()
        #expect(store.currentStep == .name)
        #expect(store.currentStepIndex == 0)
    }

    @Test("resetForm clears isGoalSkipped")
    func resetFormClearsGoalSkipped() {
        let (store, _, _, _) = makeSUT()
        store.isGoalSkipped = true
        store.resetForm()
        #expect(!store.isGoalSkipped)
    }

    @Test("resetForm creates a new SignupForm")
    func resetFormCreatesNewForm() {
        let (store, _, _, _) = makeSUT()
        store.signupForm.email.value = "dirty@example.com"
        store.resetForm()
        #expect(store.signupForm.email.value == "")
    }

    // MARK: - handleExit (when form is clean)

    @Test("handleExit dismisses directly when form is not dirty and isFromAccountSwitching")
    func handleExitDismissesWhenClean() {
        let (store, _, _, _) = makeSUT()
        var dismissed = false
        store.isFromAccountSwitching = true
        store.dismissAction = { dismissed = true }
        store.handleExit()
        #expect(dismissed)
    }

    @Test("handleExit shows exit alert when form is dirty and isFromAccountSwitching")
    func handleExitShowsAlertWhenDirty() {
        let (store, _, notificationService, _) = makeSUT()
        store.isFromAccountSwitching = true
        store.signupForm.firstName.value = "Changed"
        store.handleExit()
        #expect(notificationService.showAlertCallCount == 1)
    }

    // MARK: - Height management

    @Test("updateFormHeight correctly stores metric cm value")
    func updateFormHeightMetric() {
        let (store, _, _, _) = makeSUT()
        store.signupForm.useMetric.value = true
        store.updateFormHeight(fromMetric: true, values: ["1", "7", "5"])
        let stored = Int(store.signupForm.height.value)
        #expect(stored > 0)
    }

    @Test("updateFormHeight correctly stores imperial feet/inches value")
    func updateFormHeightImperial() {
        let (store, _, _, _) = makeSUT()
        store.signupForm.useMetric.value = false
        store.updateFormHeight(fromMetric: false, values: ["5", "9"])
        let stored = Int(store.signupForm.height.value)
        #expect(stored > 0)
    }

    // MARK: - generateProfile (via createUser indirect test)

    @Test("createUser passes profile with correct gender from form")
    func createUserPassesGenderInProfile() async {
        let (store, accountService, _, _) = makeSUT()
        store.signupForm.gender.value = Sex.female.rawValue
        store.signupForm.email.value = "test@example.com"
        store.signupForm.password.value = "pass123"
        store.isGoalSkipped = true

        await store.createUser()

        #expect(accountService.lastSignUpProfile?.gender == .female)
    }

    @Test("createUser trims leading and trailing whitespace from email")
    func createUserTrimsEmail() async {
        let (store, accountService, _, _) = makeSUT()
        store.signupForm.email.value = "  spaced@example.com  "
        store.signupForm.password.value = "pass123"
        store.isGoalSkipped = true

        await store.createUser()

        #expect(accountService.lastSignUpEmail == "spaced@example.com")
    }

    // MARK: - getFormattedHeight

    @Test("getFormattedHeight returns non-empty string in imperial mode")
    func getFormattedHeightImperial() {
        let (store, _, _, _) = makeSUT()
        store.signupForm.useMetric.value = false
        let result = store.getFormattedHeight()
        #expect(!result.isEmpty)
    }

    @Test("getFormattedHeight returns non-empty string in metric mode")
    func getFormattedHeightMetric() {
        let (store, _, _, _) = makeSUT()
        store.signupForm.useMetric.value = true
        let result = store.getFormattedHeight()
        #expect(!result.isEmpty)
    }

    // MARK: - showHeightPicker

    @Test("showHeightPicker sets showHeightCmPicker when useMetric is true")
    func showHeightPickerMetric() {
        let (store, _, _, _) = makeSUT()
        store.signupForm.useMetric.value = true
        store.showHeightPicker()
        #expect(store.showHeightCmPicker)
        #expect(!store.showHeightInchesPicker)
    }

    @Test("showHeightPicker sets showHeightInchesPicker when useMetric is false")
    func showHeightPickerImperial() {
        let (store, _, _, _) = makeSUT()
        store.signupForm.useMetric.value = false
        store.showHeightPicker()
        #expect(store.showHeightInchesPicker)
        #expect(!store.showHeightCmPicker)
    }

    // MARK: - isHeightValid via updateNextButtonState

    @Test("isNextEnabled true on height step when stored height is valid imperial")
    func isNextEnabledHeightStepValidImperial() {
        let (store, _, _, _) = makeSUT()
        store.currentStepIndex = SignupStep.height.index
        store.signupForm.useMetric.value = false
        // 5'10" = 70 inches stored as feet*12+inches=70 → use updateFormHeight
        store.updateFormHeight(fromMetric: false, values: ["5", "10"])
        store.updateNextButtonState()
        #expect(store.isNextEnabled)
    }

    @Test("isNextEnabled true on height step when stored height is valid metric")
    func isNextEnabledHeightStepValidMetric() {
        let (store, _, _, _) = makeSUT()
        store.currentStepIndex = SignupStep.height.index
        store.signupForm.useMetric.value = true
        store.updateFormHeight(fromMetric: true, values: ["1", "7", "5"])
        store.updateNextButtonState()
        #expect(store.isNextEnabled)
    }

    // MARK: - getError(for:)

    @Test("getError delegates to signupForm and returns nil for untouched control")
    func getErrorDelegatesForUntouched() {
        let (store, _, _, _) = makeSUT()
        let result = store.getError(for: store.signupForm.email)
        #expect(result == nil)
    }

    @Test("getError delegates to signupForm and returns error for touched invalid email")
    func getErrorDelegatesForTouchedInvalidEmail() {
        let (store, _, _, _) = makeSUT()
        store.signupForm.email.markAsTouched()
        store.signupForm.email.value = "bad-format"
        store.signupForm.email.validate()
        let result = store.getError(for: store.signupForm.email)
        #expect(result != nil)
    }

    // MARK: - handleEditingChanged

    @Test("handleEditingChanged touches field when isEditing becomes false")
    func handleEditingChangedOnBlur() {
        let (store, _, _, _) = makeSUT()
        #expect(!store.signupForm.firstName.isTouched)
        store.handleEditingChanged(false, field: .firstName)
        #expect(store.signupForm.firstName.isTouched)
    }

    @Test("handleEditingChanged does nothing when isEditing is true")
    func handleEditingChangedNoOpWhileEditing() {
        let (store, _, _, _) = makeSUT()
        store.handleEditingChanged(true, field: .firstName)
        #expect(!store.signupForm.firstName.isTouched)
    }

    // MARK: - showHelpModal

    @Test("showHelpModal calls notificationService.showModal once")
    func showHelpModalCallsModal() {
        let (store, _, notificationService, _) = makeSUT()
        store.showHelpModal()
        #expect(notificationService.showModalCallCount == 1)
    }

    // MARK: - generateGoalRequest via createUser (maintain mode)

    @Test("createUser generates maintain goal when goalType is maintain")
    func createUserGeneratesMaintainGoal() async {
        let (store, accountService, _, _) = makeSUT()
        store.signupForm.email.value = "test@example.com"
        store.signupForm.password.value = "pass123"
        store.isGoalSkipped = false
        store.signupForm.goalType.value = GoalType.maintain.rawValue
        store.signupForm.goalWeight.value = "150"

        await store.createUser()

        #expect(accountService.createGoalCallCount == 1)
    }

    // MARK: - updateFormHeight invalid values

    @Test("updateFormHeight rejects invalid metric values")
    func updateFormHeightRejectsInvalidMetric() {
        let (store, _, _, _) = makeSUT()
        let originalHeight = store.signupForm.height.value
        store.signupForm.useMetric.value = true
        store.updateFormHeight(fromMetric: true, values: ["0", "0", "0"])
        #expect(store.signupForm.height.value == originalHeight)
    }

    @Test("updateFormHeight rejects invalid imperial values")
    func updateFormHeightRejectsInvalidImperial() {
        let (store, _, _, _) = makeSUT()
        let originalHeight = store.signupForm.height.value
        store.signupForm.useMetric.value = false
        store.updateFormHeight(fromMetric: false, values: ["0", "0"])
        #expect(store.signupForm.height.value == originalHeight)
    }
}
