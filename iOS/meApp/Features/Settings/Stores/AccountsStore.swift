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
    
    let alertStrings = AlertStrings.self
    let appConstants = AppConstants.self
    let toastLang = ToastStrings.self
    
    var theme = Theme.shared
    
    @Published var activeAccount: Account?
    @Published var accounts: [Account] = []
    
    @Published var canShowLoginScreen = false
    @Published var canShowAccountSignupScreen = false
    
    private let tag = "AccountsStore"
    var cancellables: Set<AnyCancellable> = []
    
    init() {
        accountService.$activeAccount
            .sink { [weak self] account in
                self?.activeAccount = account
            }
            .store(in: &accountService.cancellables)
        
        accountService.$allAccounts
            .sink { [weak self] allAccounts in
                self?.accounts = allAccounts
            }
            .store(in: &accountService.cancellables)
    }
    
    func handleLoginCTA() {
        if accounts.count >= appConstants.Account.maxAccounts {
            showMaxUserAccountsAlert()
            return
        }
        canShowLoginScreen = true
    }
    
    func handleSignupCTA() {
        if accounts.count >= appConstants.Account.maxAccounts {
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
        guard let account = accounts.first(where: { $0.accountId == accountId }) else {
            logger.log(level: .error, tag: tag, message: "Account with ID \(accountId) does not exist")
            return
        }
        Task {
            notificationService.showLoader(LoaderModel(text: "Switching account..."))
            do {
                try await accountService.switchAccount(to: account)
                logger.log(level: .info, tag: tag, message: "Switched active account to \(accountId)")
                notificationService.showToast(ToastModel(message: toastLang.switchingAccount(account.firstName ?? "")))
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to switch active account", data: error.localizedDescription)
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
        
        Task {
            notificationService.showLoader(LoaderModel(text: "Removing user..."))
            do {
                try await accountService.logOut(accountId: account.accountId)
                logger.log(level: .info, tag: tag, message: "Removed user \(user.name) from account \(account.accountId)")
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to remove user \(user.name)", data: error.localizedDescription)
            }
            notificationService.dismissLoader()
        }
    }
}
