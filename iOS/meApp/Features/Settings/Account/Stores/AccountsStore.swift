//
//  AccountsStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 24/06/25.
//

import Combine
import Foundation
import SwiftUI

// MARK: - Accounts Store
/// A store that manages the state of accounts in the application.
@MainActor
class AccountsStore: ObservableObject {
    @Injector var accountService: AccountServiceProtocol
    @Injector var notificationService: NotificationHelperServiceProtocol
    @Injector var entryService: EntryServiceProtocol
    @Injector var logger: LoggerServiceProtocol
    @Injector var feedService: FeedServiceProtocol

    private let networkMonitor: NetworkMonitoring

    let alertStrings = AlertStrings.self
    let appConstants = AppConstants.self
    let toastLang = ToastStrings.self

    var theme = Theme.shared

    @Published var activeAccount: Account?
    @Published var accounts: [Account] = []
    @Published var userItems: [UserItemInfo] = []

    @Published var canShowLoginScreen = false
    /// Holds the email to prefill in `LoginScreen` when opening from account switching flow.
    @Published var emailForLogin: String?
    @Published var canShowAccountSignupScreen = false

    private let tag = "AccountsStore"
    var cancellables: Set<AnyCancellable> = []

    // swiftlint:disable:next function_body_length
    init(
        injectedAccountService: AccountServiceProtocol? = nil,
        injectedNotificationService: NotificationHelperServiceProtocol? = nil,
        injectedEntryService: EntryServiceProtocol? = nil,
        injectedLogger: LoggerServiceProtocol? = nil,
        injectedFeedService: FeedServiceProtocol? = nil,
        networkMonitor: NetworkMonitoring? = nil
    ) {
        self.networkMonitor = networkMonitor ?? NetworkMonitor.shared
        if let injectedAccountService {
            self.accountService = injectedAccountService
        }
        if let injectedNotificationService {
            self.notificationService = injectedNotificationService
        }
        if let injectedEntryService {
            self.entryService = injectedEntryService
        }
        if let injectedLogger {
            self.logger = injectedLogger
        }
        if let injectedFeedService {
            self.feedService = injectedFeedService
        }

        accountService.activeAccountPublisher
            .sink { [weak self] account in
                self?.activeAccount = account
            }
            .store(in: &cancellables)

        // Watch allAccounts and update both `accounts` and `userItems`.
        // Show every saved account — logged-in, expired, and manually logged-out.
        // Accounts only disappear when explicitly removed from the device.
        accountService.allAccountsPublisher
            .sink { [weak self] allAccounts in
                guard let self = self else { return }

                // Split by login state: fully logged-in vs anything that needs re-login
                let loggedInAccounts = allAccounts.filter {
                    $0.isLoggedIn == true && ($0.isExpired ?? false) == false
                }
                let loggedOutAccounts = allAccounts.filter {
                    $0.isLoggedIn != true || ($0.isExpired ?? false) == true
                }

                // Sort each group by last active time (most recent first)
                let sortByLastActive: (Account, Account) -> Bool = {
                    (DateTimeTools.parse($0.lastActiveTime ?? "") ?? .distantPast) >
                    (DateTimeTools.parse($1.lastActiveTime ?? "") ?? .distantPast)
                }
                let allSortedAccounts = loggedInAccounts.sorted(by: sortByLastActive)
                    + loggedOutAccounts.sorted(by: sortByLastActive)

                self.accounts = allSortedAccounts

                self.userItems = allSortedAccounts.map {
                    let isLoggedIn = $0.isLoggedIn == true
                    let isExpired = $0.isExpired ?? false
                    // Show "Log In" button for any account that isn't fully authenticated.
                    let needsLogin = !isLoggedIn || isExpired
                    return UserItemInfo(
                        accountID: $0.accountId,
                        name: ($0.firstName?.isEmpty == false ? $0.firstName : nil) ?? $0.email,
                        email: $0.email,
                        isSelected: $0.isActiveAccount ?? false,
                        isExpired: needsLogin,
                        canShowSelection: true
                    )
                }
            }
            .store(in: &cancellables)
    }

    /// Triggers display of `LoginScreen`. Pass the email to pre-fill if available.
    /// - Parameter email: Optional email address to prefill in the login form.
    /// - Parameter isUserExpired: Indicates if the user account is expired.
    func handleLoginCTA(email: String? = nil, isUserExpired: Bool = false) {
        // If the user is expired, allow login with the same email.
        // If the user modifies the email and the account limit has been reached, show the max accounts alert.
        // Only count logged-in accounts toward the limit, since logged-out accounts
        let loggedInCount = accounts.filter { $0.isLoggedIn == true }.count
        if loggedInCount >= appConstants.Account.maxAccounts && !isUserExpired {
            showMaxUserAccountsAlert()
            return
        }
        emailForLogin = email
        canShowLoginScreen = true
    }

    func handleSignupCTA() {
        // Only count logged-in accounts toward the limit
        let loggedInCount = accounts.filter { $0.isLoggedIn == true }.count
        if loggedInCount >= appConstants.Account.maxAccounts {
            showMaxUserAccountsAlert()
            return
        }
        canShowAccountSignupScreen = true
    }

    func showMaxUserAccountsAlert() {
        let alertLang = alertStrings.MaxUsersAlert
        let alert = AlertModel(
            title: alertLang.title,
            message: alertLang.message,
            buttons: [
                AlertButtonModel(title: alertLang.okButton, type: .primary) { _ in
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    func switchActiveAccount(to accountId: String) {
        guard let account = accounts.first(where: { $0.accountId == accountId })  else {
            logger.log(level: .error, tag: tag, message: "Account with ID \(accountId) does not exist")
            return
        }

        guard account.accountId != self.activeAccount?.accountId  else {
            logger.log(level: .error, tag: tag, message: "Attempted to switch to the same active account \(accountId)")
            return
        }

        // R1: Extract @Model data before async boundary
        let userName = account.firstName?.isEmpty == false ? account.firstName ?? account.email : account.email

        Task {
            notificationService.showLoader(LoaderModel(text: "Switching account..."))
            defer {
                notificationService.dismissLoader()
            }
            do {
                try await accountService.switchAccount(to: account)
                logger.log(level: .info, tag: tag, message: "Switched active account to \(accountId)")
                notificationService.showToast(ToastModel(message: toastLang.switchingAccount(userName)))
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to switch active account", data: error.localizedDescription)
                switch error {
                case HTTPError.noInternet, HTTPError.timeout:
                    notificationService.showToast(ToastModel(message: toastLang.unableToConnect))
                default:
                    notificationService.showToast(ToastModel(message: toastLang.somethingWentWrong))
                }
            }
        }
    }

    func userRemoveHandler(user: UserItemInfo) {
        let alertLang = alertStrings.DeleteUserAlert
        let alert = AlertModel(
            title: alertLang.title(user.name),
            message: alertLang.message(user.name),
            buttons: [
                AlertButtonModel(title: alertLang.removeButton, type: .danger) { _ in
                    self.removeUser(user: user)
                },
                AlertButtonModel(title: alertLang.cancelButton, type: .secondary) { _ in
                }
            ]
        )
        notificationService.showAlert(alert)
    }

    private func removeUser(user: UserItemInfo) {
        guard let account = accounts.first(where: { $0.accountId == user.accountID }) else {
            logger.log(level: .error, tag: tag, message: "Account with ID \(user.accountID) does not exist")
            return
        }

        // Only require connectivity when the account is still logged in (needs API logout).
        // Already-logged-out accounts can be removed from the device without a network call.
        let isLoggedIn = account.isLoggedIn == true && (account.isExpired ?? false) == false
        if isLoggedIn && !networkMonitor.isConnected {
            notificationService.showToast(ToastModel(message: toastLang.unableToConnect))
            logger.log(level: .error, tag: tag, message: "Cannot remove logged-in account while offline")
            return
        }

        let accountId = account.accountId
        Task {
            notificationService.showLoader(LoaderModel(text: "Removing user..."))
            do {
                try await accountService.removeAccountFromDevice(accountId: accountId)
                logger.log(level: .info, tag: tag, message: "Removed user \(user.name) from account \(accountId)")
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to remove user \(user.name)", data: error.localizedDescription)
                switch error {
                case HTTPError.noInternet:
                    notificationService.showToast(ToastModel(message: toastLang.unableToConnect))
                default:
                    notificationService.showToast(ToastModel(message: toastLang.somethingWentWrong))
                }
            }
            notificationService.dismissLoader()
        }
    }
}
