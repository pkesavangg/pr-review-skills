//  HealthKitStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 27/06/25.
//

import Foundation
import SwiftUI
import Combine

// MARK: - HealthKitStore
/// Centralised observable store that wraps all HealthKit related workflows.
/// It talks to `HealthKitService` for the heavy-lifting and exposes a small, UI-friendly
/// API (published properties + async helpers) so that screens only have to observe
/// this object.
@MainActor
final class HealthKitStore: ObservableObject {
    // MARK: - Published State
    /// Current on/off status of the Apple Health integration.
    @Published var isIntegrated: Bool = false
    /// Current Health-access modal state. `nil` means no modal is visible.
    @Published var activeState: AppleHealthIntegrationState? = nil
    
    // MARK: - Dependencies
    @Injector private var notificationService: NotificationHelperService
    @Injector private var entryService: EntryService
    @Injector private var accountService: AccountService
    @Injector private var integrationService: IntegrationsService
    @Injector private var healthKitService: HealthKitService
    
    var cancellables: Set<AnyCancellable> = []
    // MARK: - Init
    init() {
        loadStatus()
    }
    
    func loadStatus() {
        accountService.$activeAccount
            .sink { [weak self] account in
                self?.isIntegrated = account?.integrationSettings?.isHealthKitOn ?? false
            }
            .store(in: &cancellables)
    }
    
    /// Clears *all* HealthKit data that was previously written by the app and
    /// updates local status accordingly.
    func clearIntegration() {
        Task {
            notificationService.showLoader(LoaderModel(text: LoaderStrings.loading))
            do {
                 try await healthKitService.clearHealthKit()
            } catch {
            }
            notificationService.dismissLoader()
        }
    }
    
    // MARK: - Flow Helpers ------------------------------------------------
    /// Called when the Apple-Health row is tapped in the integrations list.
    func handleRowTap() {
        if isIntegrated {
            clearIntegration()
            return
        } else {
            // Kick off the access flow.
            activeState = .permissionsNotAllowed
        }
    }
    
    /// Handles the primary button tap for the given modal `state`.
    /// Maps UI states to store actions.
    /// - Parameter state: Current `AppleHealthIntegrationState` presented.
    func handlePrimaryAction(for state: AppleHealthIntegrationState) {
        switch state {
        case .permissionsNotAllowed:
            requestAuthorization()
        case .integrationComplete:
            finishIntegrationFlow()
        case .integrationFailed:
            healthKitService.openAppleHealth()
        case .permissionsAllowed:
            // Not used in current flow – treat same as `.permissionsNotAllowed`.
            requestAuthorization()
        case .userConflict:
            dismissModal()
        }
    }
    
    /// Dismisses the modal entirely.
    func dismissModal() {
        activeState = nil
    }
    
    // MARK: - Private Flow Steps -----------------------------------------
    
    /// Requests HealthKit authorisation via `HealthKitService`. Updates state depending on result.
    private func requestAuthorization() {
        Task {
            // notificationService.showLoader(LoaderModel(text: LoaderStrings.loading))
            do {
                let success = try await healthKitService.integrate(turnOn: true)
                // notificationService.dismissLoader()
                if success {
                    // Persisted successfully
                    activeState = .integrationComplete
                } else {
                    activeState = .integrationFailed
                }
            } catch {
                // notificationService.dismissLoader()
                activeState = .integrationFailed
            }
        }
    }
    
    /// Called when user taps **FINISH** on the *Integration Complete* screen.
    /// Presents *Sync Weight History* alert if needed, otherwise shows toast and ends flow.
    private func finishIntegrationFlow() {
        Task {
            self.dismissModal()
            do {
                let count = try await entryService.getEntryCount()
                if count > 0 {
                    presentSyncHistoryAlert()
                } else {
                    // Nothing to sync – just close flow.
                    notificationService.showToast(ToastModel(message: ToastStrings.hkIntegrationSynced))
                    dismissModal()
                }
            } catch {
                dismissModal()
            }
        }
    }
    
    /// Presents the *Sync Weight History* confirmation alert.
    private func presentSyncHistoryAlert() {
        let alert = AlertModel(
            title: AlertStrings.SyncWeightHistoryAlert.title,
            message: AlertStrings.SyncWeightHistoryAlert.message,
            buttons: [
                AlertButtonModel(title: AlertStrings.SyncWeightHistoryAlert.cancelButton, type: .secondary) { _ in
                    self.dismissModal()
                },
                AlertButtonModel(title: AlertStrings.SyncWeightHistoryAlert.syncButton, type: .primary) { _ in
                    self.performFullSync()
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Performs the actual sync and shows success toast.
    private func performFullSync() {
        Task {
            notificationService.showLoader(LoaderModel(text: LoaderStrings.loading))
            do {
                try await healthKitService.syncAllData()
                notificationService.dismissLoader()
                notificationService.showToast(ToastModel(message: ToastStrings.weightHistorySynced))
            } catch {
                notificationService.dismissLoader()
                notificationService.showToast(ToastModel(title: ToastStrings.somethingWentWrongTitle, message: ToastStrings.pleaseTryAgain))
            }
            self.dismissModal()
        }
    }
}
