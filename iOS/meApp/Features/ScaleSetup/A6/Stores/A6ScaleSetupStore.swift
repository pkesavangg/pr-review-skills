//
//  A6ScaleSetupStore.swift
//  meApp
//
//  Created by Cursor AI on 08/07/25.
//

import Foundation
import SwiftUI
import Combine

/// Store responsible for orchestrating the A6 (LCBT) scale-setup multi-step flow.
@MainActor
final class A6ScaleSetupStore: ObservableObject {
    // MARK: - Dependencies
    @Injector private var notificationService: NotificationHelperService
    // MARK: - Private
    private var scaleItem: ScaleItemInfo?
    var dismissAction: DismissAction?

    // MARK: - Published State
    @Published var currentStepIndex: Int = 0 {
        didSet {
            currentStep = steps[currentStepIndex]
        }
    }
    @Published private(set) var currentStep: A6ScaleSetupStep = .intro
    @Published private(set) var steps: [A6ScaleSetupStep] = A6ScaleSetupStep.allCases
    @Published var isNextEnabled: Bool = true

    /// Convenience accessor building the views for each step.
    var stepViews: [AnyView] {
        guard let scaleItem else { return [] }
        return steps.map { step in
            switch step {
            case .intro:
                return AnyView(ScaleSetupIntroView(scale: scaleItem))
            default:
                return AnyView(EmptyView()) // Placeholder – to be implemented later
            }
        }
    }

    // MARK: - Navigation Helpers
    func moveToNextStep() {
        guard currentStepIndex < steps.count - 1 else {
            dismissAction?()
            return
        }
        currentStepIndex += 1
    }

    func moveToPreviousStep() {
        guard currentStepIndex > 0 else { return }
        currentStepIndex -= 1
    }

    // MARK: - Configuration
    func configure(with sku: String) {
        let resolved = SCALES.first { $0.sku == sku } ?? SCALES.first
        self.scaleItem = resolved
        currentStepIndex = 0
        currentStep = steps.first ?? .intro
    }

    // MARK: - Exit / Help

    /// Presents a confirmation alert before abandoning the setup flow.
    func handleExit() {
        let alertLang = AlertStrings.ExitSetupAlert.self
        let alert = AlertModel(
            title: alertLang.title,
            message: alertLang.message,
            buttons: [
                AlertButtonModel(title: alertLang.exitButton, type: .primary) { [weak self] _ in
                    self?.dismissAction?()
                },
                AlertButtonModel(title: alertLang.returnButton, type: .secondary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }
} 
