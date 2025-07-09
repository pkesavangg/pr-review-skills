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
    /// Centralised permission handling service.
    @Injector private var permissionsService: PermissionsService

    // MARK: - Published State
    @Published var currentStepIndex: Int = 0 {
        didSet {
            currentStep = steps[currentStepIndex]
            updateNextEnabled()
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
            case .permissions:
                return AnyView(PermissionListView(setupType: .bluetooth))
            default:
                return AnyView(EmptyView()) // Placeholder – to be implemented later
            }
        }
    }

    // MARK: - Navigation Helpers
    func moveToNextStep() {
        var nextIndex = currentStepIndex + 1

        // Skip the permissions page if the Bluetooth permission is already granted.
        while nextIndex < steps.count,
              steps[nextIndex] == A6ScaleSetupStep.permissions,
              isBluetoothPermissionEnabled() {
            nextIndex += 1
        }

        guard nextIndex < steps.count else {
            dismissAction?()
            return
        }

        currentStepIndex = nextIndex
    }

    func moveToPreviousStep() {
        var previousIndex = currentStepIndex - 1

        // Skip the permissions page when navigating backwards if already satisfied.
        while previousIndex >= 0,
              steps[previousIndex] == A6ScaleSetupStep.permissions,
              isBluetoothPermissionEnabled() {
            previousIndex -= 1
        }

        guard previousIndex >= 0 else { return }
        currentStepIndex = previousIndex
    }

    // MARK: - Configuration
    func configure(with sku: String) {
        let resolved = SCALES.first { $0.sku == sku } ?? SCALES.first
        self.scaleItem = resolved
        currentStepIndex = 0
        currentStep = steps.first ?? .intro

        // Evaluate initial next-button state.
        updateNextEnabled()
    }

    // MARK: - Private
    private var cancellables = Set<AnyCancellable>()

    /// Resolved scale metadata used across the setup flow.
    private var scaleItem: ScaleItemInfo?
    /// Callback used by the screen to dismiss itself.
    var dismissAction: DismissAction?
    
    /// Evaluates whether the required Bluetooth permission has already been granted.
    private func isBluetoothPermissionEnabled() -> Bool {
        // The PermissionService tracks GG SDK permissions. Treat `ENABLED` as satisfied.
        permissionsService.getPermissionState(.BLUETOOTH) == .ENABLED &&
        permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED
    }

    /// Updates `isNextEnabled` depending on the current step and permission state.
    private func updateNextEnabled() {
        guard currentStep == .permissions else {
            isNextEnabled = true
            return
        }
        isNextEnabled = isBluetoothPermissionEnabled()
    }

    // MARK: - Lifecycle
    init() {
        // Observe permission updates so the footer button reacts instantly.
        permissionsService.$permissions
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.updateNextEnabled()
            }
            .store(in: &cancellables)
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
