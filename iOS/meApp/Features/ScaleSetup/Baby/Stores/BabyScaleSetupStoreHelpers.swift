//
//  BabyScaleSetupStoreHelpers.swift
//  meApp
//

import Foundation

@MainActor
extension BabyScaleSetupStore {

    // MARK: - Exit Cleanup

    func performExitCleanup() {
        // Cancel all subscriptions
        resetDiscoveryState()
        cancellables.removeAll()

        // Mark setup as no longer in progress
        bluetoothService.isSetupInProgress = false

        // Resume normal scanning
        Task { await bluetoothService.resyncAndScan() }

        LoggerService.shared.log(level: .info, tag: tag, message: "Baby scale setup cleanup complete")

        // Dismiss the screen
        dismissAction?()
    }

    // MARK: - Cleanup

    /// Breaks retain cycles when the store is being deallocated.
    func cleanup() {
        cancellables.removeAll()
        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        scanTimeoutTask?.cancel()
        scanTimeoutTask = nil
        dismissAction = nil
    }
}
