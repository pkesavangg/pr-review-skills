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
    var alertLang = AlertStrings.self
    
    @Published var currentStepIndex: Int = SignupStep.name.index {
        didSet {
            currentStep = steps[currentStepIndex]
            updateNextButtonState()
        }
    }
    @Published private(set) var currentStep: SignupStep = .name
    @Published var signupForm = SignupForm()
    @Published var isNextEnabled = false
    
    private var cancellables = Set<AnyCancellable>()

    init() {
        setupFormObservers()
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
            isNextEnabled = (signupForm.firstName.isValid && signupForm.lastName.isValid)
        case .dateOfBirth:
            isNextEnabled = (signupForm.birthday.isValid)
        case .sex:
            isNextEnabled = signupForm.gender.isValid
        case .height:
            isNextEnabled = signupForm.height.isValid
        case .goal:
            isNextEnabled = signupForm.goalType.isValid
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
        // TODO: Implement help modal logic
    }
    
    private func setupFormObservers() {
        signupForm.formDidChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in
                self?.updateNextButtonState()
            }
            .store(in: &cancellables)
    }
}
