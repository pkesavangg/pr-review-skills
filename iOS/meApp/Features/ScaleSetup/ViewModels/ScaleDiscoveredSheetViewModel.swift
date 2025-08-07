//  ScaleDiscoveredSheetViewModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 10/07/25.
//
//  View-model powering `ScaleDiscoveredSheetView`, responsible for:
//  • Auto-dismissing the sheet after a timeout (15 s by default).
//  • Calling `BluetoothService.disconnectDevice` when the user actively closes the sheet.
//  • Exposing the discovered `Device` and optional `DeviceDiscoveryEvent` to the UI.

import Foundation
import Combine

@MainActor
final class ScaleDiscoveredSheetViewModel: ObservableObject {
    // MARK: – Input
    let device: Device
    let discoveryEvent: DeviceDiscoveryEvent?
    private let onTimeout: () -> Void
    
    // MARK: – Dependencies
    @Injector private var bluetoothService: BluetoothService
    private var timerTask: Task<Void, Never>? = nil
    private let timeoutConstants = AppConstants.TimeoutsAndRetention.self
    // MARK: – Lifecycle
    init(device: Device,
         discoveryEvent: DeviceDiscoveryEvent?,
         onTimeout: @escaping () -> Void) {
        self.device = device
        self.discoveryEvent = discoveryEvent
        self.onTimeout = onTimeout
        startAutoDismissTimer()
    }
    
    // MARK: – Public API
    /// Invoked when the user taps the close (×) icon.
    func handleClose() {
        disconnectDevice()
        onTimeout() // reuse same close handler
    }
    
    /// Invoked when the user taps the "Connect" button.
    func clearTimer() {
        // Cancel the timer task if it exists
        timerTask?.cancel()
        timerTask = nil
    }
    
    // MARK: – Private Helpers
    private func startAutoDismissTimer() {
        timerTask = Task {
            // Sleep for the requested timeout, then invoke the callback on the main actor.
            try? await Task.sleep(nanoseconds: UInt64(timeoutConstants.discoveredAlertTimeout))
            await MainActor.run { [weak self] in
                guard let self else { return }
                // Also disconnect when auto-dismissing.
                self.disconnectDevice()
                self.onTimeout()
            }
        }
    }
    
    private func disconnectDevice() {
        guard let broadcastId = device.broadcastIdString, !broadcastId.isEmpty else { return }
        Task {
            _ = await bluetoothService.disconnectDevice(broadcastId: broadcastId)
        }
    }
    
    deinit {
        timerTask?.cancel()
    }
}
