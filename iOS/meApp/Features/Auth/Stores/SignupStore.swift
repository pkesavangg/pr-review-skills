//
//  SignupStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 11/06/25.
//

import Combine
// This store intentionally aggregates all signup flow logic to maintain
// a single source of truth for the multi-step signup process. Splitting would
// fragment state management and reduce maintainability.
import Foundation
import SwiftUI

// MARK: SignupStore
/// This store is responsible for managing the signup process.
@MainActor
final class SignupStore: ObservableObject {
    @Injector var notificationService: NotificationHelperServiceProtocol
    @Injector var accountService: AccountServiceProtocol
    @Injector var logger: LoggerServiceProtocol
    var alertLang = AlertStrings.self
    var loaderLang = LoaderStrings.self
    var commonLang = CommonStrings.self
    
    @Published var currentStepIndex: Int = 0 {
        didSet {
            currentStep = steps[currentStepIndex]
            updateNextButtonState()
        }
    }
    @Published private(set) var currentStep: SignupStep = .name
    @Published var signupForm = SignupForm()
    @Published var isNextEnabled = false
    @Published var isGoalSkipped = false

    // Device type selection
    @Published var selectedDeviceType: SignupDeviceType?

    // Baby management
    @Published var babies: [SignupBaby] = []
    @Published var isEditingBabyIndex: Int?
    @Published var showBabySexPicker = false
    @Published var showBabyDatePicker = false

    // Height-related published properties
    @Published var selectedHeightInches: [String] = ["5", "10"]  // Default 5'10"
    @Published var selectedHeightCm: [String] = ["1", "7", "8"]  // Default 178cm
    @Published var showHeightInchesPicker = false
    @Published var showHeightCmPicker = false

    @Published var isFromAccountSwitching: Bool = false
    
    var onSignupSuccess: (() -> Void)?
    var dismissAction: DismissAction?
    
    let heightInchesOptions = ConversionTools.heightInchesOptions
    let heightCmOptions     = ConversionTools.heightCmOptions
    
    private let toastLang = ToastStrings.self
    private var cancellables = Set<AnyCancellable>()
    private var previousMetricValue: Bool = false
    
    private let tag = "SignupStore"
    
    init() {
        // Resolve once per store instance to avoid cross-test DI races when
        // async step actions execute after other suites reset the container.
        _ = notificationService
        _ = accountService
        _ = logger

        previousMetricValue = signupForm.useMetric.value
        setupFormObservers()
        self.updateWeightValidators(isMetric: self.signupForm.useMetric.value)
        updateHeightPickerValues(from: Int(signupForm.height.value))
        self.updateWeightValidators(isMetric: self.signupForm.useMetric.value)
    }
    
    /// The ordered steps for the current signup flow.
    /// Dynamically computed based on the selected device type.
    var steps: [SignupStep] {
        var result: [SignupStep] = [.name, .dateOfBirth, .pickDevice]
        switch selectedDeviceType {
        case .babyScale:
            // Baby: add baby → baby list → straight to email/password
            result += [.addBaby, .babyList]
        case .bpm:
            // BPM: only sex selection, no height/goal
            result.append(.sex)
        default:
            // Weight scale (default): full flow
            result += [.sex, .height, .goal]
        }
        result += [.email, .password]
        return result
    }
    
    var progressValue: Double {
        Double(currentStepIndex + 1) / Double(steps.count)
    }
    
    // MARK: - Height Management
    
    func updateHeightPickerValues(from storedHeight: Int) {
        let selections = ConversionTools.pickerSelections(from: storedHeight)
        selectedHeightInches = selections.inches
        selectedHeightCm     = selections.cm
    }
    
    func getFormattedHeight() -> String {
        let storedHeight = Int(signupForm.height.value)
        return ConversionTools.convertToFormattedHeight(storedHeight, isMetric: signupForm.useMetric.value)
    }
    
    func updateFormHeight(fromMetric: Bool, values: [String]) {
        // Validate height before updating
        guard ConversionTools.isValidHeightPickerValues(fromMetric: fromMetric, values: values) else {
            logger.log(level: .error, tag: tag, message: "Invalid height values rejected: \(values)")
            return
        }
        
        if fromMetric {
            let cm = Int(values.joined()) ?? 178
            // Double-check cm is valid
            guard ConversionTools.isValidHeightCm(cm) else {
                logger.log(level: .error, tag: tag, message: "Invalid cm height rejected: \(cm)")
                return
            }
            signupForm.height.value = Double(ConversionTools.convertCmToStoredHeight(cm))
        } else {
            let feet = Int(values[0]) ?? 5
            let inches = Int(values[1]) ?? 10
            // Double-check feet/inches is valid
            guard ConversionTools.isValidHeightInches(feet: feet, inches: inches) else {
                logger.log(level: .error, tag: tag, message: "Invalid feet/inches height rejected: \(feet)'\(inches)\"")
                return
            }
            let totalInches = (feet * 12) + inches
            signupForm.height.value = Double(ConversionTools.convertInchesToStoredHeight(totalInches))
        }
        // Update both picker values to stay in sync
        updateHeightPickerValues(from: Int(signupForm.height.value))
    }
    
    func showHeightPicker() {
        if signupForm.useMetric.value {
            showHeightCmPicker = true
        } else {
            showHeightInchesPicker = true
        }
    }
    
    // MARK: - Navigation

    func handleSkip() {
        switch currentStep {
        case .goal:
            isGoalSkipped = true
            signupForm.resetGoal()
            objectWillChange.send()
            moveToNextStep()
        case .addBaby:
            // Skip baby flow entirely — jump to email step
            signupForm.resetBaby()
            objectWillChange.send()
            if let emailIndex = steps.firstIndex(of: .email) {
                currentStepIndex = emailIndex
            }
        default:
            objectWillChange.send()
            moveToNextStep()
        }
    }

    func moveToNextStep() {
        if currentStep == .password {
            Task {
                await createUser()
            }
        }
        // When leaving addBaby, save the baby and move to babyList
        if currentStep == .addBaby {
            saveBabyFromForm()
        }
        guard currentStepIndex < steps.count - 1 else { return }
        currentStepIndex += 1
    }

    func moveToPreviousStep() {
        guard currentStepIndex > 0 else { return }
        currentStepIndex -= 1
        if currentStep == .goal {
            isGoalSkipped = false
        }
    }

    /// Called when user changes device type on the Pick a Device step.
    func selectDeviceType(_ type: SignupDeviceType) {
        selectedDeviceType = type
        updateNextButtonState()
    }

    // MARK: - Baby Management

    /// Saves the current baby form fields as a new baby (or updates an existing one).
    func saveBabyFromForm() {
        let baby = SignupBaby(
            name: signupForm.babyName.value,
            birthday: signupForm.babyBirthday.value,
            sex: Sex(rawValue: signupForm.babySex.value),
            birthLength: signupForm.babyBirthLength.value,
            birthWeight: signupForm.babyBirthWeight.value
        )
        if let editIndex = isEditingBabyIndex {
            babies[editIndex] = baby
            isEditingBabyIndex = nil
        } else {
            babies.append(baby)
        }
        signupForm.resetBaby()
    }

    /// Navigates to the addBaby step to add another baby from the baby list.
    func addAnotherBaby() {
        signupForm.resetBaby()
        isEditingBabyIndex = nil
        guard let addBabyIndex = steps.firstIndex(of: .addBaby) else { return }
        currentStepIndex = addBabyIndex
    }

    /// Loads a baby into the form for editing and navigates to the addBaby step.
    func editBaby(at index: Int) {
        guard index < babies.count else { return }
        let baby = babies[index]
        signupForm.babyName.value = baby.name
        signupForm.babyBirthday.value = baby.birthday
        signupForm.babySex.value = baby.sex?.rawValue ?? ""
        signupForm.babyBirthLength.value = baby.birthLength
        signupForm.babyBirthWeight.value = baby.birthWeight
        isEditingBabyIndex = index
        guard let addBabyIndex = steps.firstIndex(of: .addBaby) else { return }
        currentStepIndex = addBabyIndex
    }

    /// Removes a baby at the given index.
    func deleteBaby(at index: Int) {
        guard index < babies.count else { return }
        babies.remove(at: index)
        updateNextButtonState()
    }
    
    func updateNextButtonState() {
        switch currentStep {
        case .name:
            isNextEnabled = (signupForm.firstName.isValid && signupForm.lastName.isValid)
        case .dateOfBirth:
            isNextEnabled = (signupForm.birthday.isValid)
        case .pickDevice:
            isNextEnabled = selectedDeviceType != nil
        case .addBaby:
            isNextEnabled = signupForm.babyName.isValid
        case .babyList:
            isNextEnabled = !babies.isEmpty
        case .sex:
            isNextEnabled = signupForm.gender.isValid
        case .height:
            isNextEnabled = isHeightValid
        case .goal:
            isNextEnabled = isGoalStepValid()
        case .email:
            isNextEnabled = signupForm.email.isValid
        case .password:
            // Check individual field validations AND form-level password match validation
            let fieldsValid = signupForm.password.isValid && signupForm.confirmPassword.isValid && signupForm.zipcode.isValid
            let passwordsMatch = !signupForm.formErrors[.passwordMatch]
            isNextEnabled = fieldsValid && passwordsMatch
        }
    }
    
    /// Checks if the current height value is valid.
    /// For metric: height must be >= 100 cm
    /// For imperial: height must be >= 2'0" (24 inches)
    private var isHeightValid: Bool {
        let storedHeight = Int(signupForm.height.value)
        
        if signupForm.useMetric.value {
            let cm = ConversionTools.convertStoredHeightToCm(storedHeight)
            return ConversionTools.isValidHeightCm(cm)
        } else {
            let feetInches = ConversionTools.convertStoredHeightToFeet(storedHeight)
            return ConversionTools.isValidHeightInches(feet: feetInches[0], inches: feetInches[1])
        }
    }
    
    func getError<T>(for control: FormControl<T>) -> String? {
        signupForm.getError(for: control)
    }

    // MARK: - Field Touch / Validation (Signup)
    
    /// Marks a specific field as touched and triggers validation.
    /// Used by signup input views to show field errors as soon as the user leaves a field
    /// or presses the keyboard "Next/Done" button.
    /// - Parameter field: The field to touch and validate.
    func touchAndValidate(field: FocusField) {
        var didUpdate = true
        
        switch field {
        case .firstName:
            signupForm.firstName.markAsTouched()
            signupForm.firstName.validate()
        case .lastName:
            signupForm.lastName.markAsTouched()
            signupForm.lastName.validate()
        case .email:
            signupForm.email.markAsTouched()
            signupForm.email.validate()
        case .password:
            signupForm.password.markAsTouched()
            signupForm.password.validate()
            signupForm.validate()
        case .confirmPassword:
            signupForm.confirmPassword.markAsTouched()
            signupForm.confirmPassword.validate()
            signupForm.validate()
        case .zipCode:
            signupForm.zipcode.markAsTouched()
            signupForm.zipcode.validate()
        case .currentWeight:
            signupForm.currentWeight.markAsTouched()
            signupForm.currentWeight.validate()
            signupForm.validate()
        case .goalWeight:
            signupForm.goalWeight.markAsTouched()
            signupForm.goalWeight.validate()
            signupForm.validate()
        case .babyName:
            signupForm.babyName.markAsTouched()
            signupForm.babyName.validate()
        case .babyBirthLength:
            signupForm.babyBirthLength.markAsTouched()
            signupForm.babyBirthLength.validate()
        case .babyBirthWeight:
            signupForm.babyBirthWeight.markAsTouched()
            signupForm.babyBirthWeight.validate()
        default:
            didUpdate = false
        }
        
        if didUpdate {
            objectWillChange.send()
        }
    }
    
    /// Call this from `onEditingChanged` for fields where we want to validate on blur.
    func handleEditingChanged(_ isEditing: Bool, field: FocusField) {
        guard !isEditing else { return }
        touchAndValidate(field: field)
    }
    
    func handleExit(router: Router<AuthRoute>? = nil) {
        // If the form is not dirty, dismiss the signup screen
        if !signupForm.isDirty {
            if isFromAccountSwitching {
                dismissAction?()
            } else {
                router?.navigateBack()
            }
            return
        }
        let alert = AlertModel(
            title: alertLang.SignupExitAlert.title,
            message: alertLang.SignupExitAlert.message,
            buttons: [
                AlertButtonModel(title: alertLang.SignupExitAlert.exitButton, type: .primary) { _ in
                    if self.isFromAccountSwitching {
                        self.dismissAction?()
                    } else {
                        router?.navigateBack()
                        self.resetForm()
                    }
                },
                AlertButtonModel(title: alertLang.SignupExitAlert.returnButton, type: .secondary) { _ in
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    func showHelpModal() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(HelpModalView {
                self.notificationService.dismissModal()
            })
        ))
    }
    
    func createUser() async {
        notificationService.showLoader(LoaderModel(text: loaderLang.creatingAccount))
        
        let email = removeWhiteSpace(signupForm.email.value)
        let password = signupForm.password.value
        logger.log(level: .info, tag: tag, message: "Signup flow started. accountSwitching=\(isFromAccountSwitching)")
        
        let profile = generateProfile()
        let goal = generateGoalRequest()
        do {
            _ = try await accountService.signUp(
                email: email,
                password: password,
                profile: profile
            )
            // Create the goal if it's not skipped
            if let goal = goal {
                logger.log(
                    level: .info,
                    tag: tag,
                    message: "Signup flow creating initial goal. goalType=\(goal.goalType.rawValue), "
                        + "goalWeight=\(goal.goalWeight), initialWeight=\(goal.initialWeight)"
                )
                _ = try await accountService.createGoal(goal)
            }
            logger.log(
                level: .success,
                tag: tag,
                message: "Signup flow succeeded. goalSkipped=\(goal == nil), accountSwitching=\(isFromAccountSwitching)"
            )
            if isFromAccountSwitching {
                dismissAction?()
            } else {
                onSignupSuccess?()
            }
            resetForm()
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "Signup flow failed. error=\(error.localizedDescription), "
                    + "errorType=\(String(describing: type(of: error)))"
            )
            if case AccountError.maxAccountsReached = error {
                showMaxUserAccountsAlert()
                return
            }
            handleSignupError(error)
        }
        notificationService.dismissLoader()
    }
    
    // MARK: - Private Methods
    private func isGoalStepValid() -> Bool {
        // Use the form's isGoalValidForSave which checks: dirty, touched, and no errors
        signupForm.isGoalValidForSave
    }
    
    private func generateProfile() -> Profile {
        let formattedDOB = DateTimeTools.formatDateToYMD_Local(signupForm.birthday.value)
        
        return Profile(
            firstName: removeWhiteSpace(signupForm.firstName.value),
            lastName: removeWhiteSpace(signupForm.lastName.value),
            gender: Sex(rawValue: signupForm.gender.value) ?? .male,
            zipcode: removeWhiteSpace(signupForm.zipcode.value),
            dob: formattedDOB,
            weightUnit: signupForm.useMetric.value ? .kg : .lb,
            height: signupForm.height.value,
            activityLevel: .normal
        )
    }
    
    private func generateGoalRequest() -> Goal? {
        guard !isGoalSkipped else { return nil }
        
        let useMetric = signupForm.useMetric.value
        let goalTypeValue = signupForm.goalType.value
        let current = Double(signupForm.currentWeight.value) ?? 0.0
        let target = Double(signupForm.goalWeight.value) ?? 0.0
        
        let convert = { (weight: Double) -> Int in
            ConversionTools.convertDisplayToStored(weight, forceMetric: useMetric)
        }
        
        if goalTypeValue == GoalType.maintain.rawValue {
            return Goal(
                type: .maintain,
                goalWeight: convert(target),
                initialWeight: 0,
                goalType: .maintain
            )
        } else {
            let derivedType: GoalType = target > current ? .gain : .lose
            return Goal(
                type: derivedType,
                goalWeight: convert(target),
                initialWeight: convert(current),
                goalType: derivedType
            )
        }
    }
    
    private func handleSignupError(_ error: Error) {
        var toastMessage: String?
        let toastTitle: String = toastLang.errorCreatingAccount

        switch error {
        case HTTPError.apiError(let message, _):
            if message == commonLang.emailAlreadyInUse {
                toastMessage = toastLang.emailInUse
            } else {
                toastMessage = toastLang.somethingWentWrong
            }
        case HTTPError.badRequest:
            toastMessage = toastLang.emailInUse
        case HTTPError.noInternet:
            break // No message needed, handled by NetworkMonitor
        case HTTPError.serverError:
            toastMessage = toastLang.serverError
        default:
            toastMessage = toastLang.somethingWentWrong
        }

        if let message = toastMessage {
            notificationService.showToast(ToastModel(title: toastTitle, message: message))
        }
        logger.log(
            level: .error,
            tag: tag,
            message: "Signup error handled. mappedToastShown=\(toastMessage != nil), "
                + "errorType=\(String(describing: type(of: error)))"
        )
    }

    private func setupFormObservers() { // swiftlint:disable:this function_body_length
        signupForm.formDidChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in
                self?.updateNextButtonState()
            }
            .store(in: &cancellables)
        
        // React to password-related changes only when we're on the password step
        let passwordChanges = Publishers.CombineLatest(
            signupForm.password.$value,
            signupForm.confirmPassword.$value
        )

        passwordChanges
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _, _ in
                self?.signupForm.validate()
                
                if self?.currentStep == .password {
                    self?.updateNextButtonState()
                }
            }
            .store(in: &cancellables)

        // Update button state when form errors change (password step only)
        signupForm.$formErrors
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard self?.currentStep == .password else { return }
                self?.updateNextButtonState()
            }
            .store(in: &cancellables)
        
        // Observe useMetric changes
        signupForm.useMetric.$value
            .dropFirst()
            .sink { [weak self] isMetric in
                guard let self = self else { return }
                let oldMetricValue = self.previousMetricValue
                
                // Update validators first to ensure validation uses correct unit constraints
                self.updateWeightValidators(isMetric: isMetric)
                
                // Convert weight values when switching units
                if !self.signupForm.currentWeight.value.isEmpty {
                    let convertedCurrentWeight = ConversionTools.convertDisplayWeightValue(
                        self.signupForm.currentWeight.value,
                        fromMetric: oldMetricValue,
                        toMetric: isMetric
                    )
                    self.signupForm.currentWeight.value = convertedCurrentWeight
                    self.signupForm.currentWeight.validate()
                }
                
                if !self.signupForm.goalWeight.value.isEmpty {
                    let convertedGoalWeight = ConversionTools.convertDisplayWeightValue(
                        self.signupForm.goalWeight.value,
                        fromMetric: oldMetricValue,
                        toMetric: isMetric
                    )
                    self.signupForm.goalWeight.value = convertedGoalWeight
                    self.signupForm.goalWeight.validate()
                }
                
                self.updateHeightPickerValues(from: Int(self.signupForm.height.value))
                
                // Update previous value for next change
                self.previousMetricValue = isMetric
            }
            .store(in: &cancellables)
    }
    
    private func updateWeightValidators(isMetric: Bool) {
        let maxWeight = isMetric ? 450.0 : 999.0
        // Remove old validator
        signupForm.currentWeight.removeValidator(ofType: .maxValue)
        signupForm.goalWeight.removeValidator(ofType: .maxValue)

        // Add new validator
        let validator = Validator.maxValue(maxWeight)
        signupForm.currentWeight.addValidator(validator)
        signupForm.goalWeight.addValidator(validator)
    }

    // MARK: - Form Reset

    /// Resets the signup flow back to its initial pristine state.
    /// This is useful when the user abandons the signup process and we want to
    /// discard all entered data while also preventing Combine subscription leaks.
    func resetForm() {
        // Cancel existing subscriptions so we don't leak memory.
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()

        // Replace with a brand-new form instance.
        signupForm = SignupForm()

        // Reset navigation/UI state.
        selectedDeviceType = nil
        babies.removeAll()
        isEditingBabyIndex = nil
        showBabySexPicker = false
        showBabyDatePicker = false
        currentStepIndex = 0
        isGoalSkipped = false
        isNextEnabled = false
        showHeightInchesPicker = false
        showHeightCmPicker = false
        previousMetricValue = signupForm.useMetric.value

        // Sync height pickers with the default form height.
        updateHeightPickerValues(from: Int(signupForm.height.value))

        // Re-establish observers that depend on the new form instance.
        setupFormObservers()

        // Ensure the primary action button reflects the current (reset) state.
        updateNextButtonState()
    }
    
    /// Presents an alert informing the user that the maximum number of accounts
    /// has been reached.
    private func showMaxUserAccountsAlert() {
        let alertLang = alertLang.MaxUsersAlert
        let alert = AlertModel(
            title: alertLang.title,
            message: isFromAccountSwitching ? alertLang.message : alertLang.logInAndRemoveMessage,
            buttons: [
                AlertButtonModel(title: alertLang.okButton, type: .primary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }
}
