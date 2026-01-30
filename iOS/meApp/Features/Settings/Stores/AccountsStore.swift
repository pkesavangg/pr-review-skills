//
//  AccountsStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 24/06/25.
//

import Foundation
import Combine
import SwiftUI

// MARK: - Accounts Store
/// A store that manages the state of accounts in the application.
@MainActor
class AccountsStore: ObservableObject {
    @Injector var accountService: AccountService
    @Injector var notificationService: NotificationHelperService
    @Injector var entryService: EntryService
    @Injector var logger: LoggerService
    @Injector var feedService: FeedService

    private let networkMonitor = NetworkMonitor.shared

    let alertStrings = AlertStrings.self
    let appConstants = AppConstants.self
    let toastLang = ToastStrings.self

    var theme = Theme.shared

    @Published var activeAccount: Account?
    @Published var accounts: [Account] = []
    @Published var userItems: [UserItemInfo] = []

    @Published var canShowLoginScreen = false
    /// Holds the email to prefill in `LoginScreen` when opening from account switching flow.
    @Published var emailForLogin: String? = nil
    @Published var canShowAccountSignupScreen = false

    private let tag = "AccountsStore"
    var cancellables: Set<AnyCancellable> = []

    init() {
        accountService.$activeAccount
            .sink { [weak self] account in
                self?.activeAccount = account
            }
            .store(in: &accountService.cancellables)

        // Watch allAccounts and update both `accounts` and `userItems`
        accountService.$allAccounts
            .sink { [weak self] allAccounts in
                guard let self = self else { return }

                // Show all accounts except truly expired ones (expired + logged out)
                let accountsToShow = allAccounts.filter {
                    !($0.isExpired == true && $0.isLoggedIn != true)
                }

                // Split by login state
                let loggedInAccounts = accountsToShow.filter { $0.isLoggedIn == true }
                let loggedOutAccounts = accountsToShow.filter { $0.isLoggedIn != true }

                // Sort logged-in accounts by last active time (most recent first)
                let sortedLoggedInAccounts = loggedInAccounts.sorted {
                    (DateTimeTools.parse($0.lastActiveTime ?? "") ?? .distantPast) >
                    (DateTimeTools.parse($1.lastActiveTime ?? "") ?? .distantPast)
                }
                // Sort logged-out accounts by last active time
                let sortedLoggedOutAccounts = loggedOutAccounts.sorted {
                    let lhs = DateTimeTools.parse($0.lastActiveTime ?? "") ?? .distantPast
                    let rhs = DateTimeTools.parse($1.lastActiveTime ?? "") ?? .distantPast
                    return lhs > rhs
                }

                // Combine: logged-in first, then logged-out
                let allSortedAccounts = sortedLoggedInAccounts + sortedLoggedOutAccounts
                self.accounts = allSortedAccounts

                self.userItems = allSortedAccounts.map {
                    let isLoggedIn = $0.isLoggedIn == true
                    let isExpired = $0.isExpired ?? false
                    // Show "Log In" button for logged-out accounts or auto-logged-out accounts (expired but still marked as logged in)
                    let needsLogin = !isLoggedIn || (isExpired && isLoggedIn)
                    return UserItemInfo(
                        accountID: $0.accountId,
                        name: $0.firstName?.isEmpty == false ? $0.firstName! : $0.email,
                        email: $0.email,
                        isSelected: $0.isActiveAccount ?? false,
                        isExpired: needsLogin, // Logged-out and auto-logged-out accounts show "Log In" button
                        canShowSelection: true
                    )
                }
            }
            .store(in: &accountService.cancellables)
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

        Task {
            notificationService.showLoader(LoaderModel(text: "Switching account..."))
            do {
                try await accountService.switchAccount(to: account)
                logger.log(level: .info, tag: tag, message: "Switched active account to \(accountId)")
                let userName = account.firstName?.isEmpty == false ? account.firstName ?? account.email : account.email
                notificationService.showToast(ToastModel(message: toastLang.switchingAccount(userName)))
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to switch active account", data: error.localizedDescription)
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

        guard networkMonitor.isConnected else {
            notificationService.showToast(ToastModel(message: toastLang.unableToConnect))
            logger.log(level: .error, tag: tag, message: "Cannot remove account while offline")
            return
        }

        Task {
            notificationService.showLoader(LoaderModel(text: "Removing user..."))
            do {
                try await accountService.logOut(accountId: account.accountId)
                logger.log(level: .info, tag: tag, message: "Removed user \(user.name) from account \(account.accountId)")
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
