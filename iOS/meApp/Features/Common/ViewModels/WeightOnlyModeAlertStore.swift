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
    func enableBodyMetricsForScale(_ scale: Device) {
        Task {
            do {
                // TODO: Implement the actual enabling logic based on scale type
                // This would typically involve calling the bluetooth service
                // to temporarily enable body metrics for the session


                await MainActor.run {
                    notificationService.showToast(
                        ToastModel(
                            title: WeightOnlyModeStrings.bodyMetricsEnabledMessage,
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
