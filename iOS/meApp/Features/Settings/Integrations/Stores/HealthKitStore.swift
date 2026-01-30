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
    
    @Published var isOutOfSync: Bool = false
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
    let wgTotalPermissionsCount = 5
    
    /// Retains the Combine subscription for app-active notifications specifically used
    /// when we need to re-check HealthKit permissions after the user is redirected to
    /// the Apple Health app.
    private var foregroundObserver: AnyCancellable? = nil
    
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
                isIntegrated = (result?.isIntegrated ?? false && (result?.assignedTo == accountService.activeAccount?.accountId))
                self.isOutOfSync = await self.healthKitService.isHKOutOfSync()
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
        }
        Task {
            do {
                let isAlreadyIntegrated = try await integrationService.isIntegrationAlreadyUsed(type: .healthKit)
                if isAlreadyIntegrated {
                    activeState = .userConflict
                    return
                }
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to check if HealthKit integration already exists", data: error.localizedDescription)
            }
            
            let wasPreviouslyIntegrated = await self.wasPreviouslyIntegrated()

            if wasPreviouslyIntegrated {
                // Determine the correct modal to present based on existing HealthKit permissions.
                let permissionCount = healthKitService.getApprovedPermissionList().count
                // According to WG: 5 permissions granted ⇒ show *Permissions Allowed* flow,
                // partial permissions ( >0 & <5 ) ⇒ show *Integration Complete* flow so the user can finish,
                // no permissions ⇒ proceed with normal *Permissions Not Allowed* flow.
                switch permissionCount {
                case wgTotalPermissionsCount...:
                    activeState = .permissionsAllowed
                case 1..<wgTotalPermissionsCount:
                    activeState = .integrationComplete
                default:
                    activeState = .integrationFailed
                }
            } else {
                // User has never integrated before, so show the *Permissions Not Allowed* modal.
                activeState = .permissionsNotAllowed
            }
        }
    }
    
    /// Handles the primary button tap for the given modal `state`.
    /// Maps UI states to store actions.
    /// - Parameter state: Current `AppleHealthIntegrationState` presented.
    func handlePrimaryAction(for state: AppleHealthIntegrationState) {
        Task {
            switch state {
            case .permissionsNotAllowed:
                requestAuthorization()
            case .integrationComplete:
                let wasPreviouslyIntegrated = await self.wasPreviouslyIntegrated()
                if wasPreviouslyIntegrated {
                    requestAuthorization()
                } else {
                    finishIntegrationFlow()
                }
            case .integrationFailed:
                // Open Apple Health so the user can adjust permissions, then
                // listen for the app to return to foreground to re-evaluate.
                observeForegroundForPermissionChanges()
                healthKitService.openAppleHealth()
            case .permissionsAllowed:
                let wasPreviouslyIntegrated = await self.wasPreviouslyIntegrated()
                if wasPreviouslyIntegrated {
                    requestAuthorization()
                } else {
                    finishIntegrationFlow()
                }
            case .userConflict:
                dismissModal()
            }
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
                let wasPreviouslyIntegrated = await self.wasPreviouslyIntegrated()
                let success = try await healthKitService.integrate(turnOn: true)
                if success {
                    if !wasPreviouslyIntegrated {
                        activeState = .integrationComplete
                    } else {
                        finishIntegrationFlow()
                    }
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
            // Persist the integration if it hasn't been stored yet (e.g. user had partial permissions).
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
            notificationService.showLoader(LoaderModel(text: LoaderStrings.syncing))
            do {
                try await healthKitService.syncAllData()
                // After full sync, log the most recent entry to backend.
                if let latestEntry = try await entryService.getLatestEntry() {
                    await integrationService.logHealthEntry(entry: latestEntry)
                }
                notificationService.dismissLoader()
                notificationService.showToast(ToastModel(message: ToastStrings.weightHistorySynced))
            } catch {
                notificationService.dismissLoader()
                notificationService.showToast(ToastModel(title: ToastStrings.somethingWentWrongTitle, message: ToastStrings.pleaseTryAgain))
            }
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
            notificationService.showLoader(LoaderModel(text: LoaderStrings.removingIntegration))
            do {
                try await healthKitService.clearHealthKit()
                getLocalStoredData()
                notificationService.showToast(ToastModel(message: ToastStrings.hkIntegrationRemoved))
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to clear HealthKit data", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
        }
    }
    
    /// Presents an *Apple Health Out of Sync* alert.
    func showHKOutOfSyncAlert() {
        let lang = alertLang.HKOutOfSyncAlert
        let alert = AlertModel(
            title: lang.title,
            message: lang.message,
            buttons: [
                AlertButtonModel(title: lang.closeButton, type: .primary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    private func wasPreviouslyIntegrated() async -> Bool {
        var wasPreviouslyIntegrated = false
        do {
            if let info = try await integrationService.getStoredIntegrationData() {
                // If a record exists for HealthKit, user had integrated before.
                wasPreviouslyIntegrated = info.type == .healthKit && !info.isIntegrated
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Falied to check if user previously integrated", data: error.localizedDescription)
        }
        return wasPreviouslyIntegrated
    }
    
    // MARK: - Permission change observer when opening Apple Health
    
    /// Sets up a temporary observer that fires when the app becomes active again
    /// (i.e. user returns from the Apple Health app). At that point we re-evaluate
    /// granted permissions and, if at least one permission is now granted, advance
    /// the flow to `.integrationComplete`.
    private func observeForegroundForPermissionChanges() {
        // Avoid duplicating the observer.
        if foregroundObserver != nil { return }
        
        foregroundObserver = NotificationCenter.default
            .publisher(for: UIApplication.didBecomeActiveNotification)
            .sink { [weak self] _ in
                guard let self else { return }
                // Check again on main actor.
                Task { @MainActor in
                    let permissionsGranted = self.healthKitService.getApprovedPermissionList().count
                    if permissionsGranted > 0 {
                        self.activeState = .integrationComplete
                        // Remove observer once done.
                    } else {
                        self.activeState = nil
                    }
                    self.foregroundObserver?.cancel()
                    self.foregroundObserver = nil
                }
            }
    }
}
