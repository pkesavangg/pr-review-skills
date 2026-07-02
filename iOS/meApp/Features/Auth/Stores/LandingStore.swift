import Combine
import Foundation
import SwiftUI

//  LandingStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 27/06/25.
//

/// Store backing `LandingScreen`. Provides the current list of logged-in accounts and handles account switching.
@MainActor
final class LandingStore: ObservableObject {
    // MARK: Dependencies
    @Injector private var accountService: AccountServiceProtocol
    @Injector private var notificationService: NotificationHelperServiceProtocol
    @Injector private var logger: LoggerServiceProtocol

    private let networkMonitor = NetworkMonitor.shared

    // MARK: Published State
    @Published var accounts: [AccountSnapshot] = []
    @Published var userItems: [UserItemInfo] = []

    let loadingLang = LoaderStrings.self
    let alertStrings = AlertStrings.self
    private let appConstants = AppConstants.self
    private let toastLang = ToastStrings.self

    // MARK: Private
    private var cancellables: Set<AnyCancellable> = []
    private var connectionCheckTimeout: DispatchWorkItem?
    private let tag = "LandingStore"

    // MARK: Init
    init() {
        setupAccountObservation()
        setupNetworkMonitoring()
    }

    // MARK: - Setup Methods

    /// Observes account changes and updates the local account list.
    /// All saved accounts are shown — logged-in, expired, and manually logged-out.
    /// Accounts disappear only when explicitly removed from the device.
    private func setupAccountObservation() {
        accountService.allAccountsPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] all in
                guard let self = self else { return }

                // MOB-423: every saved account appears on landing — active, manually
                // logged-out (isLoggedIn=false), and auto-logged-out (isExpired=true).
                // Accounts disappear only when explicitly removed from the device. A
                // logged-out account renders the "Logged out" card with a Log In action
                // (tapping it opens Login with the email pre-filled); an active account
                // stays tap-to-switch.
                let sortByLastActive: (AccountSnapshot, AccountSnapshot) -> Bool = { lhs, rhs in
                    let lhsDate = DateTimeTools.parse(lhs.lastActiveTime ?? "") ?? .distantPast
                    let rhsDate = DateTimeTools.parse(rhs.lastActiveTime ?? "") ?? .distantPast
                    return lhsDate > rhsDate
                }

                let sorted = all.sorted(by: sortByLastActive)

                self.accounts = sorted

                self.userItems = sorted.map { account in
                    let displayName = account.firstName?.isEmpty == false
                        ? (account.firstName ?? account.email)
                        : account.email
                    // The row view keys its logged-out treatment (dimmed card, "Logged
                    // out" label, Log In button) off `isExpired`. An account needs login
                    // whenever it isn't fully active.
                    let needsLogin = !(account.isLoggedIn == true && account.isExpired == false)
                    return UserItemInfo(
                        accountID: account.accountId,
                        name: displayName,
                        email: account.email,
                        isSelected: false,
                        isExpired: needsLogin,
                        canShowSelection: false
                    )
                }
            }
            .store(in: &cancellables)
    }

    /// Observes network connectivity changes and shows toast when connection is lost.
    /// Implements a delay mechanism to avoid false alerts during quick network toggles.
    private func setupNetworkMonitoring() {
        networkMonitor.$isConnected
            .receive(on: DispatchQueue.main)
            .sink { [weak self] isConnected in
                self?.handleNetworkStatusChange(isConnected: isConnected)
            }
            .store(in: &cancellables)
    }

    // MARK: - Network Monitoring

    /// Handles network status changes and shows toast when network disconnects.
    /// - Parameter isConnected: Current network connection status.
    private func handleNetworkStatusChange(isConnected: Bool) {
        connectionCheckTimeout?.cancel()

        guard !isConnected else { return }

        // Delay the toast to avoid false alerts during quick toggles (similar to weightGurus)
        let workItem = DispatchWorkItem { [weak self] in
            guard let self = self, !self.networkMonitor.isConnected else { return }
            self.showNoConnectionToast()
        }
        connectionCheckTimeout = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0, execute: workItem)
    }

    /// Shows a toast notification when network connection is lost.
    private func showNoConnectionToast() {
        let toast = ToastModel(
            message: toastLang.unableToConnect,
            duration: 3.0
        )
        notificationService.showToast(toast)
    }

    // MARK: Intent(s)
    /// Attempts to make the supplied account active.
    func switchAccount(to accountID: String) {
        guard let account = accounts.first(where: { $0.accountId == accountID }) else {
            logger.log(level: .error, tag: tag, message: "Account with ID \(accountID) not found")
            return
        }

        // R1: Extract @Model data before async boundary
        let userName = account.firstName?.isEmpty == false ? account.firstName ?? account.email : account.email

        Task {
            notificationService.showLoader(LoaderModel(text: loadingLang.loading))
            defer {
                notificationService.dismissLoader()
            }
            do {
                try await accountService.switchAccount(to: account.accountId)
                notificationService.showToast(ToastModel(message: toastLang.switchingAccount(userName)))
                logger.log(level: .info, tag: tag, message: "Switched active account to \(accountID)")
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to switch account", data: error.localizedDescription)
                switch error {
                case HTTPError.noInternet, HTTPError.timeout:
                    notificationService.showToast(ToastModel(message: toastLang.unableToConnect))
                default:
                    notificationService.showToast(ToastModel(message: toastLang.somethingWentWrong))
                }
            }
        }
    }

    // MARK: - Remove Account

    /// Removes an account from this device. Prompts for confirmation first.
    func removeAccount(user: UserItemInfo) {
        let alertLang = alertStrings.DeleteUserAlert
        let alert = AlertModel(
            title: alertLang.title(user.name),
            message: alertLang.message(user.name),
            buttons: [
                AlertButtonModel(title: alertLang.removeButton, type: .danger) { [weak self] _ in
                    self?.performRemoveAccount(user: user)
                },
                AlertButtonModel(title: alertLang.cancelButton, type: .secondary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }

    private func performRemoveAccount(user: UserItemInfo) {
        guard let account = accounts.first(where: { $0.accountId == user.accountID }) else {
            logger.log(level: .error, tag: tag, message: "Account with ID \(user.accountID) not found for removal")
            return
        }

        // Only require connectivity when the account is still logged in.
        let isLoggedIn = account.isLoggedIn == true && account.isExpired == false
        if isLoggedIn && !networkMonitor.isConnected {
            notificationService.showToast(ToastModel(message: toastLang.unableToConnect))
            return
        }

        let accountId = account.accountId
        Task {
            notificationService.showLoader(LoaderModel(text: "Removing user..."))
            defer { notificationService.dismissLoader() }
            do {
                try await accountService.removeAccountFromDevice(accountId: accountId)
                logger.log(level: .info, tag: tag, message: "Removed account \(accountId) from device")
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to remove account \(accountId)", data: error.localizedDescription)
                switch error {
                case HTTPError.noInternet, HTTPError.timeout:
                    notificationService.showToast(ToastModel(message: toastLang.unableToConnect))
                default:
                    notificationService.showToast(ToastModel(message: toastLang.somethingWentWrong))
                }
            }
        }
    }

    // MARK: - Max Accounts Handling
    /// Returns `true` if another account can be added. If the maximum number of
    /// accounts has already been reached, shows an alert and returns `false`.
    func canAddMoreAccounts() -> Bool {
        // Only count fully logged-in accounts toward the limit.
        let loggedInCount = accounts.filter {
            $0.isLoggedIn == true && $0.isExpired == false
        }.count
        if loggedInCount >= appConstants.Account.maxAccounts {
            showMaxUserAccountsAlert()
            return false
        }
        return true
    }

    /// Presents an alert informing the user that the maximum number of accounts
    /// has been reached.
    private func showMaxUserAccountsAlert() {
        let alertLang = alertStrings.MaxUsersAlert
        let alert = AlertModel(
            title: alertLang.title,
            message: alertLang.logInAndRemoveMessage,
            buttons: [
                AlertButtonModel(title: alertLang.okButton, type: .primary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }

    deinit {
      cancellables.forEach { $0.cancel() }
      cancellables.removeAll()
    }
}
