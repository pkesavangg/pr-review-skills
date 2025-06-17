//
//  SignupStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 11/06/25.
//

import Foundation
import SwiftUI
import Combine


// MARK: SignupStore
/// This store is responsible for managing the signup process.
@MainActor
final class SignupStore: ObservableObject {
    @Injector var notificationService: NotificationHelperService
    @Injector var accountService: AccountService
    var alertLang = AlertStrings.self
    var loaderLang = LoaderStrings.self
    
    @Published var currentStepIndex: Int = SignupStep.name.index {
        didSet {
            currentStep = steps[currentStepIndex]
            updateNextButtonState()
        }
    }
    @Published private(set) var currentStep: SignupStep = .name
    @Published var signupForm = SignupForm()
    @Published var isNextEnabled = false
    @Published var isGoalSkipped = false
    
    // Height-related published properties
    @Published var selectedHeightInches: [String] = ["5", "10"]  // Default 5'10"
    @Published var selectedHeightCm: [String] = ["1", "7", "8"]  // Default 178cm
    @Published var showHeightInchesPicker = false
    @Published var showHeightCmPicker = false
    
    let heightInchesOptions: [[String]] = [
        (2...7).map { "\($0)" },
        (0...11).map { "\($0)" }
    ]
    
    let heightCmOptions: [[String]] = [
        (1...2).map { "\($0)" },
        (0...9).map { "\($0)" },
        (0...9).map { "\($0)" }
    ]
    
    private let toastLang = ToastStrings.self
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        setupFormObservers()
        updateHeightPickerValues(from: Int(signupForm.height.value))
    }
    
    let steps: [SignupStep] = [
        .name,
        .dateOfBirth,
        .sex,
        .height,
        .goal,
        .email,
        .password
    ]
    
    var progressValue: Double {
        Double(currentStepIndex + 1) / Double(steps.count)
    }
    
    // MARK: - Height Management
    
    func updateHeightPickerValues(from storedHeight: Int) {
        // Update both picker values based on the stored height
        let feet = ConversionTools.convertStoredHeightToFeet(storedHeight)
        selectedHeightInches = ["\(feet[0])", "\(feet[1])"]
        
        let cm = ConversionTools.convertStoredHeightToCm(storedHeight)
        let cmString = String(format: "%03d", cm)
        selectedHeightCm = cmString.map { String($0) }
    }
    
    func getFormattedHeight() -> String {
        if signupForm.useMetric.value {
            let cm = Int(selectedHeightCm.joined()) ?? 178
            return "\(cm) cm"
        } else {
            return "\(selectedHeightInches[0])' \(selectedHeightInches[1])\""
        }
    }
    
    func updateFormHeight(fromMetric: Bool, values: [String]) {
        if fromMetric {
            let cm = Int(values.joined()) ?? 178
            signupForm.height.value = Double(ConversionTools.convertCmToStoredHeight(cm))
        } else {
            let feet = Int(values[0]) ?? 5
            let inches = Int(values[1]) ?? 10
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
        isGoalSkipped = true
        signupForm.resetGoal()
        moveToNextStep()
    }
    
    func moveToNextStep() {
        if currentStep == .password {
            Task {
                await createUser()
            }
        }
        guard currentStepIndex < steps.count - 1 else { return }
        if currentStep == .goal  {
            isGoalSkipped = false
        }
        currentStepIndex += 1
    }
    
    func moveToPreviousStep() {
        guard currentStepIndex > 0 else { return }
        currentStepIndex -= 1
    }
    
    func updateNextButtonState() {
        switch currentStep {
        case .name:
            isNextEnabled = (signupForm.firstName.isValid && signupForm.lastName.isValid)
        case .dateOfBirth:
            isNextEnabled = (signupForm.birthday.isValid)
        case .sex:
            isNextEnabled = signupForm.gender.isValid
        case .height:
            isNextEnabled = signupForm.height.isValid
        case .goal:
            isNextEnabled = isGoalStepValid()
        case .email:
            isNextEnabled = signupForm.email.isValid
        case .password:
            isNextEnabled = (signupForm.password.isValid && signupForm.confirmPassword.isValid && signupForm.zipcode.isValid)
        }
    }
    
    func getError<T>(for control: FormControl<T>) -> String? {
        signupForm.getError(for: control)
    }
    
    func showExitAlert() {
        let alert = AlertModel(
            title: alertLang.SignupExitAlert.title,
            message: alertLang.SignupExitAlert.message,
            buttons: [
                AlertButtonModel(title: alertLang.SignupExitAlert.exitButton, type: .primary) { _ in
                    // TODO: handle exit logic
                },
                AlertButtonModel(title: alertLang.SignupExitAlert.returnButton, type: .secondary) { _ in
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    func showHelpModal() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(HelpModalView(){
                self.notificationService.dismissModal()
            })
        ))
    }
    
    func createUser() async {
        notificationService.showLoader(LoaderModel(text: loaderLang.creatingAccount))
        
        let email = removeWhiteSpace(signupForm.email.value)
        let password = signupForm.password.value
        
        let profile = generateProfile()
        let goal = generateGoalRequest()
        do {
            let _ = try await accountService.signUp(
                email: email,
                password: password,
                profile: profile
            )
            // Create the goal if it's not skipped
            if let goal = goal {
                let _ = try await accountService.createGoal(goal)
            }
        } catch {
            handleSignupError(error)
        }
        notificationService.dismissLoader()
    }
    
    // MARK: - Private Methods
    private func isGoalStepValid() -> Bool {
        if signupForm.goalType.value == GoalType.maintain.rawValue {
            return signupForm.goalWeight.isValid
        } else {
            return signupForm.currentWeight.isValid &&
            signupForm.goalWeight.isValid &&
            !signupForm.formErrors[.weightEqual]
        }
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
        
        let convert = { (w: Double) -> Int in
            ConversionTools.convertDisplayToStored(w, forceMetric: useMetric)
        }
        
        if goalTypeValue == GoalType.maintain.rawValue {
            return Goal(
                type: .maintain,
                goalWeight: convert(target),
                initialWeight: convert(target),
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
    }
    
    private func setupFormObservers() {
        signupForm.formDidChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in
                self?.updateNextButtonState()
            }
            .store(in: &cancellables)
        // Observe useMetric changes
        signupForm.useMetric.$value
            .dropFirst()
            .sink { [weak self] isMetric in
                guard let self = self else { return }
                self.updateHeightPickerValues(from: Int(self.signupForm.height.value))
                self.updateWeightValidators(isMetric: isMetric)
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
}
