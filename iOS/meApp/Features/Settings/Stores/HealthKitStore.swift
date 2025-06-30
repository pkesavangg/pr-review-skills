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
    @Injector private var logger: LoggerService
    
    var cancellables: Set<AnyCancellable> = []
    
    let alertLang = AlertStrings.self
    let tag = "HealthKitStore"
    // MARK: - Init
    init() {
        loadStatus()
    }
    
    func loadStatus() {
        self.getLocalStoredData()
    }
    
    func getLocalStoredData() {
        Task {
            do {
                let result = try await self.integrationService.getStoredIntegrationData()
                isIntegrated = ((result?.isIntegrated) != nil)
            } catch  {
                logger.log(level: .error, tag: tag, message: "Failed to load integration data", data: error.localizedDescription)
            }
        }
    }
    
    // MARK: - Flow Helpers ------------------------------------------------
    /// Called when the Apple-Health row is tapped in the integrations list.
    func handleRowTap() {
        if isIntegrated {
            showHKRemoveAlert()
            return
        } else {
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
            break
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
                if success {
                    activeState = .integrationComplete
                } else {
                    activeState = .integrationFailed
                }
                getLocalStoredData()
            } catch {
                switch error {
                case IntegrationError.userConflict:
                    activeState = .userConflict
                default:
                    activeState = .integrationFailed
                }
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
    
    private func showHKRemoveAlert() {
        let hkRemoveAlertLang = alertLang.HKRemoveAlert
        let alert = AlertModel(
            title: hkRemoveAlertLang.title,
            message: hkRemoveAlertLang.message,
            buttons: [
                AlertButtonModel(title: hkRemoveAlertLang.cancelButton, type: .secondary) { _ in
                },
                AlertButtonModel(title: hkRemoveAlertLang.removeButton, type: .danger) { _ in
                    self.clearIntegration()
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Clears *all* HealthKit data that was previously written by the app and
    /// updates local status accordingly.
    private func clearIntegration() {
        Task {
            notificationService.showLoader(LoaderModel(text: LoaderStrings.loading))
            do {
                try await healthKitService.clearHealthKit()
                getLocalStoredData()
            } catch {
            }
            notificationService.dismissLoader()
        }
    }
}
