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
    // MARK: - Private
    private var cancellables = Set<AnyCancellable>()
    
    /// Resolved scale metadata used across the setup flow.
    private var scaleItem: ScaleItemInfo?
    /// Callback used by the screen to dismiss itself.
    var dismissAction: DismissAction?
    
    // MARK: - Published State
    @Published var currentStepIndex: Int = 0 {
        didSet {
            currentStep = steps[currentStepIndex]
            updateNextEnabled()
        }
    }
    
    // Observe step changes to trigger the timers.
    @Published private(set) var currentStep: A6ScaleSetupStep = .intro {
        didSet { handleStepChange() }
    }
    // Connection status shown on the BluetoothConnectionView.
    @Published var connectionState: ConnectionState = .loading

    /// All steps in the setup flow. Exposed as read-only so views can iterate.
    @Published private(set) var steps: [A6ScaleSetupStep] = A6ScaleSetupStep.allCases

    /// Controls the enabled state of the footer "Next" button.
    @Published var isNextEnabled: Bool = true

    /// Task handling time-based transitions during testing.
    private var stepTimerTask: Task<Void, Never>? = nil
    private let tag = "A6ScaleSetupStore"
    private let scaleSetupStrings = ScaleSetupStrings.self
    
    /// Convenience accessor building the views for each step.
    var stepViews: [AnyView] {
        guard let scaleItem else { return [] }
        return steps.map { step in
            switch step {
            case .intro:
                return AnyView(ScaleSetupIntroView(scale: scaleItem))
            case .permissions:
                return AnyView(PermissionListView(setupType: .bluetooth))
            case .wakeUp:
                return AnyView(ConnectionPromptView(
                    subtitle: scaleSetupStrings.wakeYourScaleSubtitle
                ))
            case .connectingBluetooth:
                return AnyView(BluetoothConnectionView(state: connectionState))
            case .setupFinished:
                let lang = scaleSetupStrings.FinishViewStrings.self
                return AnyView(
                    ScaleSetupFinishView(title: lang.title, description: lang.description)
                        .environmentObject(Theme.shared)
                )
            }
        }
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
    
    // MARK: - Navigation Helpers
    func moveToNextStep() {
        let nextIndex = adjustedIndex(from: currentStepIndex + 1, direction: 1)
        guard nextIndex < steps.count else {
            dismissAction?()
            return
        }
        currentStepIndex = nextIndex
    }
    
    func moveToPreviousStep() {
        let previousIndex = adjustedIndex(from: currentStepIndex - 1, direction: -1)
        guard previousIndex >= 0 else { return }
        currentStepIndex = previousIndex
    }

    // MARK: - Step Change Handling

    private func handleStepChange() {
        // TODO: Implement step change handling logic.
        // Cancel any outstanding timer when changing steps.
        stepTimerTask?.cancel()

        switch currentStep {
        case .wakeUp:
            // After 3 seconds, automatically move to the next step.
            stepTimerTask = Task {
                try? await Task.sleep(nanoseconds: 3_000_000_000)
                await MainActor.run { [weak self] in
                    self?.moveToNextStep()
                }
            }

        case .connectingBluetooth:
            // Simulate the connection lifecycle: loading → failure → success.
            connectionState = .loading
            stepTimerTask = Task {
                // After 2 s show failure.
                try? await Task.sleep(nanoseconds: 2_000_000_000)
                await MainActor.run { [weak self] in self?.connectionState = .failure }

                // After 3 s show success.
                try? await Task.sleep(nanoseconds: 3_000_000_000)
                await MainActor.run { [weak self] in self?.connectionState = .success }

                // After 2 s move to the finish step.
                try? await Task.sleep(nanoseconds: 2_000_000_000)
                await MainActor.run { [weak self] in self?.moveToNextStep() }
            }

        default:
            break
        }
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

        // Evaluate individual Bluetooth-related permissions
        let bluetoothEnabled = permissionsService.getPermissionState(.BLUETOOTH) == .ENABLED
        let bluetoothSwitchEnabled = permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED

        // Automatically request the missing permission giving priority to `.bluetooth`
        if !bluetoothEnabled {
            Task { await permissionsService.handlePermission(.bluetooth) }
        } else if !bluetoothSwitchEnabled {
            Task { await permissionsService.handlePermission(.bluetoothSwitch) }
        }

        // Enable the Next button only when both permissions are granted
        isNextEnabled = bluetoothEnabled && bluetoothSwitchEnabled
    }

    /// Returns an adjusted step index by skipping the *permissions* page when the Bluetooth
    /// permission requirements are already fulfilled.
    /// - Parameters:
    ///   - index: The candidate index to navigate to.
    ///   - direction: `+1` when moving forward; `-1` when moving backwards.
    /// - Returns: A new index that omits the permissions page if it can be skipped.
    private func adjustedIndex(from index: Int, direction: Int) -> Int {
        var idx = index
        while idx >= 0 && idx < steps.count,
              steps[idx] == .permissions,
              isBluetoothPermissionEnabled() {
            idx += direction
        }
        return idx
    }
    
    // Cancel timers on deinit.
    deinit {
        stepTimerTask?.cancel()
    }
}
