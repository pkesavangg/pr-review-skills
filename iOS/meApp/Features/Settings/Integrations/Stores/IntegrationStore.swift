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
    @Injector private var logger: LoggerServiceProtocol
    @Injector private var notificationService: NotificationHelperServiceProtocol
    @Injector private var accountService: AccountServiceProtocol
    @Injector private var integrationsService: IntegrationServiceProtocol
    @Injector private var productTypeStore: ProductTypeStoreProtocol

    var cancellables: Set<AnyCancellable> = []
    @Published var accountID = ""
    private let networkMonitor: NetworkMonitoring

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
    /// Fitbit and MyFitnessPal are only shown for weight scale users.
    @Published var integrations: [IntegrationItem] = []

    // Tracks the integration operation awaiting confirmation from the API.
    private var pendingAction: PendingIntegrationAction?

    // Ensures we prompt for invalid integrations only once per screen lifecycle.
    private var hasCheckedInvalidIntegrations = false

    private enum PendingIntegrationAction {
        case connect(IntegrationItemType)
        case disconnect(IntegrationItemType)
    }

    /// Initializes the store and starts observing account changes so the UI always reflects the latest integration state.
    init(networkMonitor: NetworkMonitoring? = nil) {
        self.networkMonitor = networkMonitor ?? NetworkMonitor.shared
        // Eagerly resolve injected dependencies so this store remains stable even if
        // the shared DI container is reset later (for example, by other tests).
        _ = logger
        _ = notificationService
        _ = accountService
        _ = integrationsService
        _ = productTypeStore
        // Initialize the integrations list with any pre-defined items.
        accountService.activeAccountPublisher
            .sink { [weak self] account in
                guard let self else { return }
                self.applyAccountState(account)
                /// Check for invalid integrations only once per screen lifecycle.
                if !self.hasCheckedInvalidIntegrations, !self.skipInvalidIntegrationsCheck {
                    self.handleInvalidIntegrations(account: account)
                    self.hasCheckedInvalidIntegrations = true
                }
            }
            .store(in: &cancellables)

        // Re-render the device-driven sections when the set of paired devices changes
        // (e.g. a weight scale paired while this screen is open), so the relevant
        // integration section appears/disappears automatically without needing an
        // account-level change to fire. The first emission is dropped because the
        // account publisher above already seeds the initial state. (MOB-407)
        productTypeStore.availableItemsPublisher
            .dropFirst()
            .sink { [weak self] _ in
                guard let self else { return }
                self.applyAccountState(self.accountService.activeAccount)
            }
            .store(in: &cancellables)
    }

    /// Applies the `AccountSnapshot` state to local observable properties that drive the UI.
    /// Updates the following observable properties:
    ///   - `accountID`: Set to `account.accountId` if `account` is not nil, otherwise set to an empty string.
    ///   - `integrations`: Set to reflect the integration settings in `account` if present; if `account` is nil, both integrations are set to
    /// `isSelected: false`.
    /// - Parameter account: The latest account value (optional when stream emits nil).
    private func applyAccountState(_ account: AccountSnapshot?) {
        accountID = account?.accountId ?? ""

        // Fitbit and MyFitnessPal are only relevant for weight scale users
        var items: [IntegrationItem] = []
        if productTypeStore.availableItems.contains(.myWeight) {
            let fitbitOn = account?.isFitbitOn ?? false
            let mfpOn = account?.isMfpOn ?? false
            items.append(.init(type: .fitbit, isSelected: fitbitOn))
            items.append(.init(type: .myFitnessPal, isSelected: mfpOn))
        }
        integrations = items
    }

    /// Updates the currently selected integration ensuring only one item is selected at a time.
    /// - Parameter item: The integration item to select.
    func selectIntegration(item: IntegrationItem) {
        logger.log(
            level: .info,
            tag: tag,
            message: "Integration row selected. provider=\(integrationProviderKey(item.type)), "
                + "currentlySelected=\(item.isSelected), accountId=\(accountID)"
        )
        // If the provider is already connected, ask for confirmation to remove.
        if item.isSelected {
            pendingAction = .disconnect(item.type)
            logger.log(
                level: .info,
                tag: tag,
                message: "Integration disconnect confirmation requested. provider=\(integrationProviderKey(item.type))"
            )
            showRemoveIntegrationAlert(for: item)
            return
        }

        // Otherwise start the OAuth flow to connect the provider.
        guard let urlString = item.type.oauthURL(accountId: accountID),
              let link = URL(string: urlString) else { return }

        // If network unavailable, show fallback alert instead of launching browser
        guard networkMonitor.isConnected else {
            logger.log(level: .error, tag: tag, message: "Integration connect blocked: no internet. provider=\(integrationProviderKey(item.type))")
            showLinkOpenError(link: link)
            return
        }

        pendingAction = .connect(item.type)
        browserURL = link
        showBrowser = true
        logger.log(
            level: .info,
            tag: tag,
            message: "Presenting integration browser modal. provider=\(integrationProviderKey(item.type)), url=\(link.absoluteString)"
        )
    }

    /// Refreshes the account after an OAuth flow completes and evaluates the result of the pending integration operation.
    func refreshAccounts() {
        logger.log(level: .info, tag: tag, message: "Refreshing account after integration browser flow")
        Task {
            do {
                try await accountService.refreshAccount()
                let account = accountService.activeAccount
                self.applyAccountState(account)
                handlePostIntegrationResult(using: account)
                logger.log(level: .success, tag: tag, message: "Integration post-browser account refresh succeeded")
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
        logger.log(level: .info, tag: tag, message: "Remove integration started. provider=\(integrationProviderKey(item.type))")
        notificationService.showLoader(LoaderModel(text: LoaderStrings.removingIntegration))
        do {
            guard let provider = mapToIntegrationType(item.type) else { return }
            try await integrationsService.removeIntegration(provider)
            try await accountService.refreshAccount()
            let account = accountService.activeAccount
            applyAccountState(account)
            handlePostIntegrationResult(using: account)
            logger.log(level: .success, tag: tag, message: "Remove integration succeeded. provider=\(integrationProviderKey(item.type))")
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
    private func handlePostIntegrationResult(using account: AccountSnapshot?) {
        guard let account, let action = pendingAction else { return }

        switch action {
        case let .connect(type):
            if isIntegrationEnabled(type, in: account) {
                logger.log(
                    level: .success,
                    tag: tag,
                    message: "Integration connect completed. provider=\(integrationProviderKey(type)), accountId=\(account.accountId)"
                )
                showDoneAlert()
            } else {
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "Integration connect verification failed. provider=\(integrationProviderKey(type)), accountId=\(account.accountId)"
                )
                showTryAgainAlert(for: type, isConnect: true)
            }
        case let .disconnect(type):
            if !isIntegrationEnabled(type, in: account) {
                logger.log(
                    level: .success,
                    tag: tag,
                    message: "Integration disconnect completed. provider=\(integrationProviderKey(type)), accountId=\(account.accountId)"
                )
                showDoneAlert()
            } else {
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "Integration disconnect verification failed. provider=\(integrationProviderKey(type)), accountId=\(account.accountId)"
                )
                showTryAgainAlert(for: type, isConnect: false)
            }
        }

        // Clear pending action
        pendingAction = nil
    }

    /// Returns the enabled flag for the given integration based on the provided account.
    private func isIntegrationEnabled(_ type: IntegrationItemType, in account: AccountSnapshot) -> Bool {
        switch type {
        case .fitbit:
            return account.isFitbitOn
        case .myFitnessPal:
            return account.isMfpOn
        case .appleHealth:
            return account.isHealthKitOn
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
    private func handleInvalidIntegrations(account: AccountSnapshot?) {
        guard let account else { return }

        // Skip if network offline – user can't fix anyway.
        guard networkMonitor.isConnected else { return }

        var invalid: [IntegrationItemType] = []
        if account.isFitbitOn, !account.isFitbitValid { invalid.append(.fitbit) }
        if account.isMfpOn, !account.isMfpValid { invalid.append(.myFitnessPal) }
        // Add other providers when supported.

        guard !invalid.isEmpty else { return }
        logger.log(
            level: .info,
            tag: tag,
            message: "Detected invalid integrations requiring re-auth. providers=\(invalid.map { integrationProviderKey($0) })"
        )

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
        logger.log(level: .info, tag: tag, message: "Silent remove integration started. provider=\(integrationProviderKey(type))")
        do {
            try await integrationsService.removeIntegration(provider)
            try await accountService.refreshAccount()
            logger.log(level: .success, tag: tag, message: "Silent remove integration succeeded. provider=\(integrationProviderKey(type))")
            return true
        } catch {
            logger.log(level: .error, tag: tag, message: "Silent remove failed", data: error.localizedDescription)
            return false
        }
    }

    private func integrationProviderKey(_ type: IntegrationItemType) -> String {
        switch type {
        case .appleHealth: return "apple_health"
        case .fitbit: return "fitbit"
        case .myFitnessPal: return "my_fitness_pal"
        }
    }

    // MARK: - Request New Integration

    private let integrationLang = IntegrationsStrings.self

    /// Presents the "Request an Integration" modal where users can submit a free-text request.
    func showRequestIntegrationModal() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(RequestIntegrationModalView(
                onSend: { [weak self] text in
                    guard let self else { return }
                    notificationService.dismissModal()
                    Task { await self.submitIntegrationRequest(text: text) }
                },
                onCancel: { [weak self] in
                    self?.notificationService.dismissModal()
                }
            )),
            backdropDismiss: true
        ))
    }

    func submitIntegrationRequest(text: String) async {
        notificationService.showLoader(LoaderModel(text: LoaderStrings.sendingRequest))
        do {
            try await integrationsService.requestNewIntegration(text: text)
            notificationService.dismissLoader()
            showRequestIntegrationSuccessAlert()
            logger.log(level: .success, tag: tag, message: "Request new integration sent (\(text.count) chars)")
        } catch {
            notificationService.dismissLoader()
            showRequestIntegrationErrorAlert()
            logger.log(level: .error, tag: tag, message: "Failed to send integration request", data: error.localizedDescription)
        }
    }

    private func showRequestIntegrationSuccessAlert() {
        let alert = AlertModel(
            title: integrationLang.requestIntegrationSuccessTitle,
            message: integrationLang.requestIntegrationSuccessMessage,
            buttons: [
                AlertButtonModel(title: integrationLang.requestIntegrationSuccessDismiss, type: .primary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }

    private func showRequestIntegrationErrorAlert() {
        let alert = AlertModel(
            title: integrationLang.requestIntegrationErrorTitle,
            message: integrationLang.requestIntegrationErrorMessage,
            buttons: [
                AlertButtonModel(title: integrationLang.requestIntegrationErrorDismiss, type: .primary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }
}
