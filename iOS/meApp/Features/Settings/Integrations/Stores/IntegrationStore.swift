//  IntegrationStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 26/06/25.
//

import Combine
import Foundation
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

// MARK: - IntegrationStore
/// Observable store that manages the list of integrations shown in `IntegrationsScreen`.
/// Holds the selection state and exposes helper APIs to update it.
@MainActor
class IntegrationStore: ObservableObject {
    @Injector private var logger: LoggerService
    @Injector private var notificationService: NotificationHelperService
    @Injector private var accountService: AccountService
    @Injector private var integrationsService: IntegrationsService
    
    var cancellables: Set<AnyCancellable> = []
    @Published var accountID = ""
    
    // MARK: - In-App Browser State
    /// Controls whether the Safari browser is presented.
    @Published var showBrowser: Bool = false
    /// The URL to load in the in-app browser.
    @Published var browserURL: URL?
    @Published var skipInvalidIntegrationsCheck = false
    
    let appConstants = AppConstants.self.Product
    let commonLang = CommonStrings.self
    let alertLang = AlertStrings.self
    
    let tag = "IntegrationStore"
    
    /// Convenience URL used by the view modifier – guaranteed non-optional.
    var presentingBrowserURL: URL {
        browserURL ?? AppConstants.LegalURLs.greaterGoodsWebsite
    }
    
    /// List of integrations to display.
    @Published var integrations: [IntegrationItem] = [
        .init(type: .fitbit, isSelected: false),
        .init(type: .myFitnessPal, isSelected: false)
    ]
    
    // Tracks the integration operation awaiting confirmation from the API.
    private var pendingAction: PendingIntegrationAction?
    
    // Ensures we prompt for invalid integrations only once per screen lifecycle.
    private var hasCheckedInvalidIntegrations = false
    
    private enum PendingIntegrationAction {
        case connect(IntegrationItemType)
        case disconnect(IntegrationItemType)
    }
    
    /// Initializes the store and starts observing account changes so the UI always reflects the latest integration state.
    init() {
        // Initialize the integrations list with any pre-defined items.
        accountService.$activeAccount
            .sink { [weak self] account in
                guard let self else { return }
                self.applyAccountState(account)
                /// Check for invalid integrations only once per screen lifecycle.
                if !self.hasCheckedInvalidIntegrations && !self.skipInvalidIntegrationsCheck {
                    self.handleInvalidIntegrations(settings: account?.integrationSettings)
                    self.hasCheckedInvalidIntegrations = true
                }
            }
            .store(in: &cancellables)
    }

    /// Applies the `Account` state to local observable properties that drive the UI.
    /// Updates the following observable properties:
    ///   - `accountID`: Set to `account.accountId` if `account` is not nil, otherwise set to an empty string.
    ///   - `integrations`: Set to reflect the integration settings in `account` if present; if `account` is nil, both integrations are set to `isSelected: false`.
    /// - Parameter account: The latest account value (optional when stream emits nil).
    private func applyAccountState(_ account: Account?) {
        let fitbitOn = account?.integrationSettings?.isFitbitOn ?? false
        let mfpOn = account?.integrationSettings?.isMfpOn ?? false
        accountID = account?.accountId ?? ""
        self.integrations = [
            .init(type: .fitbit, isSelected: fitbitOn),
            .init(type: .myFitnessPal, isSelected: mfpOn)
        ]
    }
    
    /// Updates the currently selected integration ensuring only one item is selected at a time.
    /// - Parameter item: The integration item to select.
    func selectIntegration(item: IntegrationItem) {
        // If the provider is already connected, ask for confirmation to remove.
        if item.isSelected {
            pendingAction = .disconnect(item.type)
            showRemoveIntegrationAlert(for: item)
            return
        }
        
        // Otherwise start the OAuth flow to connect the provider.
        guard let urlString = item.type.oauthURL(accountId: accountID),
              let link = URL(string: urlString) else { return }
        
        // If network unavailable, show fallback alert instead of launching browser
        guard NetworkMonitor.shared.isConnected else {
            showLinkOpenError(link: link)
            return
        }

        pendingAction = .connect(item.type)
        browserURL = link
        showBrowser = true
    }
    
    /// Refreshes the account after an OAuth flow completes and evaluates the result of the pending integration operation.
    func refreshAccounts() {
        Task {
            do {
                let account = try await accountService.refreshAccount()
                self.applyAccountState(account)
                handlePostIntegrationResult(using: account)
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to refresh accounts", data: error.localizedDescription)
            }
        }
    }
    
    // MARK: - Removal Flow
    
    /// Presents a confirmation alert before removing the selected integration.
    private func showRemoveIntegrationAlert(for item: IntegrationItem) {
        let alert = AlertModel(
            title: alertLang.RemoveIntegrationAlert.title,
            message: nil,
            buttons: [
                AlertButtonModel(
                    title: alertLang.RemoveIntegrationAlert.cancelButton,
                    type: .secondary
                ) { _ in },
                AlertButtonModel(
                    title: alertLang.RemoveIntegrationAlert.removeButton,
                    type: .danger
                ) { _ in
                    Task { await self.performRemoveIntegration(item: item) }
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Executes the removal of an integration, displaying a loader while the operation runs.
    private func performRemoveIntegration(item: IntegrationItem) async {
        notificationService.showLoader(LoaderModel(text: LoaderStrings.removingIntegration)) 
        do {
            guard let provider = mapToIntegrationType(item.type) else { return }
            try await integrationsService.removeIntegration(provider)
            let account = try await accountService.refreshAccount()
            self.applyAccountState(account)
            handlePostIntegrationResult(using: account)
        } catch {
            switch error {
            case HTTPError.noInternet:
                break // No need to log network errors here, handled in the service.
            default:
                showTryAgainAlert(for: item.type, isConnect: false)
            }
            logger.log(level: .error, tag: tag, message: "Failed to remove integration", data: error.localizedDescription)
        }
        notificationService.dismissLoader()
    }
    
    /// Maps the UI-specific `IntegrationItemType` to the domain `IntegrationType` used by services.
    private func mapToIntegrationType(_ type: IntegrationItemType) -> IntegrationType? {
        switch type {
        case .fitbit: return .fitbit
        case .myFitnessPal: return .myFitnessPal
        default: return nil
        }
    }
    
    // MARK: - Result Handling
    
    /// Verifies the result of the pending integration action and displays appropriate alerts.
    private func handlePostIntegrationResult(using account: Account?) {
        guard let account, let action = pendingAction else { return }
        
        switch action {
        case .connect(let type):
            if isIntegrationEnabled(type, in: account) {
                showDoneAlert()
            } else {
                showTryAgainAlert(for: type, isConnect: true)
            }
        case .disconnect(let type):
            if !isIntegrationEnabled(type, in: account) {
                showDoneAlert()
            } else {
                showTryAgainAlert(for: type, isConnect: false)
            }
        }
        
        // Clear pending action
        pendingAction = nil
    }
    
    /// Returns the enabled flag for the given integration based on the provided account.
    private func isIntegrationEnabled(_ type: IntegrationItemType, in account: Account) -> Bool {
        switch type {
        case .fitbit:
            return account.integrationSettings?.isFitbitOn ?? false
        case .myFitnessPal:
            return account.integrationSettings?.isMfpOn ?? false
        case .appleHealth:
            return account.integrationSettings?.isHealthKitOn ?? false
        }
    }
    
    /// Presents a generic success alert when an integration operation completes successfully.
    private func showDoneAlert() {
        let alert = AlertModel(
            title: commonLang.done,
            message: nil,
            buttons: [
                AlertButtonModel(title: commonLang.ok, type: .primary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Presents an alert offering the user to retry or cancel after a failed connect/disconnect attempt.
    private func showTryAgainAlert(for type: IntegrationItemType, isConnect: Bool) {
        let alert = AlertModel(
            title: alertLang.IntegrationFailureAlert.message,
            buttons: [
                AlertButtonModel(title: commonLang.cancel, type: .secondary) { _ in },
                AlertButtonModel(title: commonLang.retry, type: .primary) { _ in
                    if isConnect {
                        if let item = self.integrations.first(where: { $0.type == type }) {
                            self.selectIntegration(item: item)
                        }
                    } else {
                        Task { [weak self] in
                            guard let self else { return }
                            self.pendingAction = .disconnect(type)
                            await self.performRemoveIntegration(item: IntegrationItem(type: type, isSelected: true))
                        }
                    }
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    // MARK: - Link Error Alert
    
    /// Shows an alert that allows the user to copy the link when the in-app browser cannot be opened.
    private func showLinkOpenError(link: URL) {
        let alert = AlertModel(
            title: alertLang.LinkOpenErrorAlert.title,
            message: "\(alertLang.LinkOpenErrorAlert.message)",
            buttons: [
                AlertButtonModel(title: alertLang.LinkOpenErrorAlert.dismissButton.uppercased(), type: .secondary) { _ in },
                AlertButtonModel(title: alertLang.LinkOpenErrorAlert.copyLinkButton.uppercased(), type: .primary) { _ in
                    #if canImport(UIKit)
                    UIPasteboard.general.string = link.absoluteString
                    #endif
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    // MARK: - Invalid Integration Check
    
    /// Checks for enabled but invalid integrations and shows a prompt to disable them.
    private func handleInvalidIntegrations(settings: IntegrationSettings?) {
        guard let settings else { return }
        
        // Skip if network offline – user can't fix anyway.
        guard NetworkMonitor.shared.isConnected else { return }
        
        var invalid: [IntegrationItemType] = []
        if settings.isFitbitOn && !settings.isFitbitValid { invalid.append(.fitbit) }
        if settings.isMfpOn && !settings.isMfpValid { invalid.append(.myFitnessPal) }
        // Add other providers when supported.
        
        guard !invalid.isEmpty else { return }
        
        let names = invalid.map { $0.displayName }.joined(separator: ", ")
        let disableLabel = invalid.count > 1
            ? alertLang.ReIntegrateAlert.disableAllButton
            : alertLang.ReIntegrateAlert.disableButton(names)
        
        let alert = AlertModel(
            title: "",
            message: alertLang.ReIntegrateAlert.message(names, invalid.count),
            buttons: [
                AlertButtonModel(title: disableLabel, type: .danger) { _ in
                    Task { [weak self] in
                        guard let self else { return }
                        var allRemoved = true
                        for type in invalid {
                            self.pendingAction = .disconnect(type)
                            let success = await self.performRemoveIntegrationSilently(type: type)
                            allRemoved = allRemoved && success
                        }
                        if allRemoved {
                            self.showDoneAlert()
                        }
                    }
                },
                AlertButtonModel(title: alertLang.ReIntegrateAlert.okButton, type: .secondary) { _ in }
            ]
        )
        
        notificationService.showAlert(alert)
    }
    
    /// Removes integration without showing remove-confirm alert/loader, returns success flag.
    private func performRemoveIntegrationSilently(type: IntegrationItemType) async -> Bool {
        guard let provider = mapToIntegrationType(type) else { return false }
        do {
            try await integrationsService.removeIntegration(provider)
            _ = try await accountService.refreshAccount()
            return true
        } catch {
            logger.log(level: .error, tag: tag, message: "Silent remove failed", data: error.localizedDescription)
            return false
        }
    }
}
