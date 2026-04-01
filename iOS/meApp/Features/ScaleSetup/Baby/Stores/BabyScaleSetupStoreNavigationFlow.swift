//
//  BabyScaleSetupStoreNavigationFlow.swift
//  meApp
//

import Foundation
import GGBluetoothSwiftPackage
import SwiftUI

@MainActor
extension BabyScaleSetupStore {

    var nextButtonText: String {
        switch currentStep {
        case .paired:
            return lang.Buttons.continueButton
        case .babyProfile:
            return lang.Buttons.save
        case .babyAdded:
            return lang.Buttons.finish
        default:
            return lang.Buttons.next
        }
    }

    // MARK: - Navigation

    func moveToNextStep() {
        guard !isExiting else { return }
        dismissKeyboard()
        let nextIndex = adjustedIndex(from: currentStepIndex + 1, direction: 1)
        guard nextIndex < steps.count else {
            handleFinish()
            return
        }
        currentStepIndex = nextIndex
    }

    func moveToPreviousStep() {
        guard !isExiting else { return }
        dismissKeyboard()
        let previousIndex = adjustedIndex(from: currentStepIndex - 1, direction: -1)
        guard previousIndex >= 0 else { return }
        currentStepIndex = previousIndex
    }

    func navigateToStep(_ step: BabyScaleSetupStep) {
        dismissKeyboard()
        guard let index = steps.firstIndex(of: step) else { return }
        currentStepIndex = index
    }

    private func dismissKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }

    /// Skips steps that should be bypassed during navigation:
    /// - `.permissions` if BT permissions are already granted (both directions)
    /// - `.wakeup` and `.connectingBluetooth` when going backwards (avoids re-triggering scan)
    func adjustedIndex(from index: Int, direction: Int) -> Int {
        var idx = index
        while idx >= 0 && idx < steps.count {
            let step = steps[idx]
            if step == .permissions && arePermissionsEnabled() {
                idx += direction
            } else if direction == -1 && (step == .wakeup || step == .connectingBluetooth) {
                idx += direction
            } else {
                break
            }
        }
        return idx
    }

    /// Returns true when BT permissions are already granted so the permissions step can be skipped.
    func arePermissionsEnabled() -> Bool {
        let states = bluetoothPermissionStates()
        return states.isBluetoothEnabled && states.isBluetoothSwitchEnabled
    }

    /// Checks current Bluetooth permission states from the permissions service.
    func bluetoothPermissionStates() -> (isBluetoothEnabled: Bool, isBluetoothSwitchEnabled: Bool) {
        (
            permissionsService.getPermissionState(.BLUETOOTH) == .ENABLED,
            permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED
        )
    }

    // MARK: - Footer Visibility

    /// Steps where the footer buttons should be hidden.
    private var stepsToHideFooter: Set<BabyScaleSetupStep> {
        [.wakeup, .connectingBluetooth]
    }

    func shouldShowFooter() -> Bool {
        !stepsToHideFooter.contains(currentStep)
    }

    /// Whether the back button should be visible for the current step.
    func shouldShowBackButton() -> Bool {
        return true
    }

    /// Whether the back button should be disabled (e.g. on the first step).
    func isBackButtonDisabled() -> Bool {
        currentStep == .intro
    }

    // MARK: - Configuration

    func configure(with sku: String,
                   discoveredScale: Device? = nil,
                   discoveryEvent: DeviceDiscoveryEvent? = nil) {
        let lookupSku = DeviceHelper.mapSkuForDisplay(sku)
        let resolved = SCALES.first { $0.sku == lookupSku } ?? SCALES.first
        self.scaleItem = resolved

        isExiting = false
        scaleSetupError = .none
        isScaleSaved = false

        bluetoothService.isSetupInProgress = true

        // Inject discovery context if provided.
        self.discoveredScale = discoveredScale
        self.discoveryEvent = discoveryEvent

        // Subscribe to permission changes
        permissionsService.permissionsPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.updateNextEnabled()
            }
            .store(in: &cancellables)

        // Subscribe to nickname form changes
        scaleNicknameForm.formDidChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in
                self?.updateNextEnabled()
            }
            .store(in: &cancellables)

        // Subscribe to baby profile form changes
        babyProfileForm.formDidChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in
                self?.updateNextEnabled()
            }
            .store(in: &cancellables)

        LoggerService.shared.log(
            level: .info,
            tag: tag,
            message: "Baby scale setup started for SKU: \(sku)"
        )

        // If a discovered scale was passed in and permissions are granted, skip to connecting
        if discoveredScale != nil && discoveryEvent != nil && arePermissionsEnabled() {
            navigateToStep(.connectingBluetooth)
        } else if discoveredScale != nil && discoveryEvent != nil {
            navigateToStep(.permissions)
        }
    }
}
