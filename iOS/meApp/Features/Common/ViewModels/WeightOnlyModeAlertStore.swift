//
//  WeightOnlyModeAlertStore.swift
//  meApp
//
//  Created by AI Assistant on 26/06/25.
//

import Foundation
import Combine

/// Store for managing weight-only mode alert state and actions
@MainActor
final class WeightOnlyModeAlertStore: ObservableObject {
    // MARK: - Dependencies
    @Injector private var scaleService: ScaleService
    @Injector private var bluetoothService: BluetoothService
    @Injector private var notificationService: NotificationHelperService

    // MARK: - Published Properties
    @Published var isLoading = false
    @Published var weightOnlyScales: [Device] = []
    @Published var showingScaleList = false

    // MARK: - Private Properties
    private var cancellables = Set<AnyCancellable>()

    // MARK: - Initialization
    init() {
        setupObservers()
    }

    // MARK: - Public Methods

    /// Loads scales that have weight-only mode enabled by other users
    func loadWeightOnlyScales() {
        isLoading = true

        Task {
            do {
                let allScales = try await scaleService.getDevices()
                let filteredScales = allScales.filter { scale in
                    scale.isWeighOnlyModeEnabledByOthers == true
                }

                await MainActor.run {
                    self.weightOnlyScales = filteredScales
                    self.isLoading = false
                }
            } catch {
                await MainActor.run {
                    self.weightOnlyScales = []
                    self.isLoading = false
                }
            }
        }
    }

    /// Enables body metrics for the specified scale temporarily
    /// - Parameter scale: The scale to enable body metrics for
    func enableBodyMetricsForScale(onCancel: (() -> Void)? = nil) {
       let alert = AlertModel(
          title: AlertStrings.EnableBodyMetricsAlert.title,
            message: AlertStrings.EnableBodyMetricsAlert.message,
            buttons: [
                AlertButtonModel(title: AlertStrings.EnableBodyMetricsAlert.enableButton, type: .primary) { _ in
                    self.handleEnableBodyMetrics()
                },
                AlertButtonModel(title: AlertStrings.EnableBodyMetricsAlert.cancelButton, type: .secondary) { _ in
                    onCancel?()
                }
            ]
        )
        notificationService.showAlert(alert)

    }

    func handleEnableBodyMetrics() {
        Task {
            do {
                // TODO: Implement the actual enabling logic based on scale type
                // This would typically involve calling the bluetooth service
                // to temporarily enable body metrics for the session


                await MainActor.run {
                    notificationService.showToast(
                        ToastModel(
                            message: WeightOnlyModeStrings.temporaryOverride
                        )
                    )
                }
            } catch {
                await MainActor.run {
                    notificationService.showToast(
                        ToastModel(
                            title: WeightOnlyModeStrings.enableFailedTitle,
                            message: WeightOnlyModeStrings.enableFailedMessage
                        )
                    )
                }
            }
        }
    }

    func dismissWeightOnlyModeAlert(onCancel: (() -> Void)? = nil) {
         let alert = AlertModel(
          title: AlertStrings.DisableWeightOnlyModeAlert.title,
            message: AlertStrings.DisableWeightOnlyModeAlert.message,
            buttons: [
                AlertButtonModel(title: AlertStrings.DisableWeightOnlyModeAlert.dismissButton, type: .primary) { _ in
                  self.bluetoothService.handleWeightOnlyModeAlertDismissed()
                },
                AlertButtonModel(title: AlertStrings.DisableWeightOnlyModeAlert.cancelButton, type: .secondary) { _ in
                    onCancel?()
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    // MARK: - Private Methods

    /// Sets up observers for device discovery
    private func setupObservers() {
        bluetoothService.deviceDiscoveredPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.loadWeightOnlyScales()
            }
            .store(in: &cancellables)
    }
}
