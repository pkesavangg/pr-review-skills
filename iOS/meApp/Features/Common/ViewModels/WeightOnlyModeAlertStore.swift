//
//  WeightOnlyModeAlertStore.swift
//  meApp
//
//  Created by AI Assistant on 14/08/25.
//

import Combine
import Foundation

/// Store for managing weight-only mode alert state and actions
@MainActor
final class WeightOnlyModeAlertStore: ObservableObject {
    // MARK: - Dependencies
    private let scaleService: ScaleServiceProtocol
    @Injector private var bluetoothService: BluetoothServiceProtocol
    @Injector private var notificationService: NotificationHelperServiceProtocol

    // MARK: - Published Properties
    @Published var isLoading = false
    @Published var weightOnlyScales: [Device] = []
    @Published var showingScaleList = false

    // MARK: - Private Properties
    private var cancellables = Set<AnyCancellable>()
    private var loadTask: Task<Void, Never>?
    private var latestLoadRequestID = 0

    // MARK: - Initialization
    init(
        scaleService: ScaleServiceProtocol? = nil
    ) {
        self.scaleService = scaleService ?? Self.resolveDependency(ScaleServiceProtocol.self)
        warmInjectedDependencies()
        setupObservers()
    }

    // MARK: - Public Methods

    /// Loads scales that have weight-only mode enabled by other users
    func loadWeightOnlyScales() {
        latestLoadRequestID += 1
        let requestID = latestLoadRequestID
        isLoading = true
        loadTask?.cancel()

        loadTask = Task { @MainActor [weak self] in
            guard let self = self else { return }
            defer {
                if requestID == self.latestLoadRequestID {
                    self.isLoading = false
                }
            }

            do {
                let allScales = try await self.scaleService.getDevices()
                let filteredScales = allScales.filter { scale in
                    scale.isWeighOnlyModeEnabledByOthers == true
                }

                guard !Task.isCancelled, requestID == self.latestLoadRequestID else { return }
                self.weightOnlyScales = filteredScales
            } catch {
                guard !Task.isCancelled, requestID == self.latestLoadRequestID else { return }
                self.weightOnlyScales = []
            }
        }
    }

    /// Enables body metrics for the specified scale temporarily
    /// - Parameter scale: The scale to enable body metrics for
    func enableBodyMetricsForScale(onCancel: (() -> Void)? = nil) {
       let alert = AlertModel(
          title: AlertStrings.EnableBodyMetricsAlert.title,
// swiftlint:disable:next vertical_parameter_alignment_on_call
            message: AlertStrings.EnableBodyMetricsAlert.message,
// swiftlint:disable:next vertical_parameter_alignment_on_call
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
        // Snapshot synchronously to avoid racey reads if callers mutate state right after tap.
        let connectedScales = weightOnlyScales.filter { $0.isConnected == true }
        guard !connectedScales.isEmpty else {
            return
        }
        notificationService.showLoader(LoaderModel(text: LoaderStrings.updatingMode))

        Task { @MainActor [weak self] in
            guard let self else { return }
            // Use the same logic as ScaleSettingsStore - call updateWeightOnlyMode for connected scales.
            let result = await self.bluetoothService.updateWeightOnlyMode(on: nil) // nil means all connected scales
            self.notificationService.dismissLoader()
            switch result {
            case .success:
                self.notificationService.showToast(
                    ToastModel(
                        message: WeightOnlyModeStrings.temporaryOverride
                    )
                )
            case .failure:
                self.notificationService.showToast(
                    ToastModel(
                        title: WeightOnlyModeStrings.enableFailedTitle,
                        message: WeightOnlyModeStrings.enableFailedMessage
                    )
                )
            }
        }
    }

    @MainActor
    private func warmInjectedDependencies() {
        let injectedDependencies = (
            bluetooth: bluetoothService,
            notification: notificationService
        )
        _ = injectedDependencies
    }

    func dismissWeightOnlyModeAlert(onCancel: (() -> Void)? = nil) {
         let alert = AlertModel(
          title: AlertStrings.DisableWeightOnlyModeAlert.title,
// swiftlint:disable:next vertical_parameter_alignment_on_call
            message: AlertStrings.DisableWeightOnlyModeAlert.message,
// swiftlint:disable:next vertical_parameter_alignment_on_call
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

    private static func resolveDependency<T>(_ type: T.Type) -> T {
        guard let dependency = DependencyContainer.shared.resolve(type) else {
            let keys = DependencyContainer.shared.dependencies.keys.sorted().joined(separator: ", ")
            fatalError("Dependency \(type) is not registered in DependencyContainer. Registered keys: [\(keys)]")
        }
        return dependency
    }
}
