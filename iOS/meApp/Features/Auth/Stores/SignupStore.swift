import Foundation
import SwiftUI
import Combine

@MainActor
final class SignupStore: ObservableObject {
    @Injector var notificationService: NotificationHelperService
    var alertLang = AlertStrings.self
    
    @Published var currentStepIndex: Int = 0 {
        didSet {
            currentStep = steps[currentStepIndex]
            updateNextButtonState()
        }
    }
    @Published private(set) var currentStep: SignupStep = .name
    @Published var formData = SignupForm()
    @Published var isNextEnabled = false
    
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
    
    func moveToNextStep() {
        guard currentStepIndex < steps.count - 1 else { return }
        currentStepIndex += 1
    }
    
    func moveToPreviousStep() {
        guard currentStepIndex > 0 else { return }
        currentStepIndex -= 1
    }
    
    func updateNextButtonState() {
        switch currentStep {
        case .name:
            isNextEnabled = (formData.firstName.isValid && formData.lastName.isValid)
        case .dateOfBirth:
            isNextEnabled = (formData.birthday.isValid)
        case .sex:
            isNextEnabled = formData.gender.isValid
        case .height:
            isNextEnabled = formData.height.isValid
        case .goal:
            isNextEnabled = formData.goalType.isValid
        case .email:
            isNextEnabled = formData.email.isValid
        case .password:
            isNextEnabled = (formData.password.isValid && formData.confirmPassword.isValid && formData.zipcode.isValid)
        }
    }
    
    func getError<T>(for control: FormControl<T>) -> String? {
        formData.getError(for: control)
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
}
