import Foundation
import Combine
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
    @Injector private var accountService: AccountService
    @Injector private var notificationService: NotificationHelperService
    @Injector private var logger: LoggerService
    
    // MARK: Published State
    @Published var accounts: [Account] = []
    @Published var userItems: [UserItemInfo] = []
    
    let loadingLang = LoaderStrings.self
    private let alertStrings = AlertStrings.self
    private let appConstants = AppConstants.self
    
    // MARK: Private
    private var cancellables: Set<AnyCancellable> = []
    private let tag = "LandingStore"
    
    // MARK: Init
    init() {
        // Keep the local list in-sync with `AccountService`.
        accountService.$allAccounts
            .receive(on: DispatchQueue.main)
            .sink { [weak self] all in
                guard let self = self else { return }
                self.accounts = all.filter { $0.isLoggedIn == true }
                
                let sortedAccounts = self.accounts.sorted { lhs, rhs in
                    let lhsDate = DateTimeTools.parse(lhs.lastActiveTime ?? "") ?? .distantPast
                    let rhsDate = DateTimeTools.parse(rhs.lastActiveTime ?? "") ?? .distantPast
                    return lhsDate > rhsDate
                }
                
                self.userItems = sortedAccounts.map { account in
                    UserItemInfo(
                        accountID: account.accountId,
                        name: account.firstName?.isEmpty == false ? account.firstName! : account.email,
                        email: account.email,
                        isSelected: false,
                        isExpired: account.isExpired ?? false,
                        canShowSelection: false
                    )
                }
            }
            .store(in: &cancellables)
    }
    
    // MARK: Intent(s)
    /// Attempts to make the supplied account active.
    func switchAccount(to accountID: String) {
        guard let account = accounts.first(where: { $0.accountId == accountID }) else {
            logger.log(level: .error, tag: tag, message: "Account with ID \(accountID) not found")
            return
        }
        
        Task {
            notificationService.showLoader(LoaderModel(text: loadingLang.loading))
            defer {
                notificationService.dismissLoader()
            }
            do {
                try await accountService.switchAccount(to: account)
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to switch account", data: error.localizedDescription)
            }
        }
    }
    
    // MARK: Max Accounts Handling
    /// Returns `true` if another account can be added. If the maximum number of
    /// accounts has already been reached, shows an alert and returns `false`.
    func canAddMoreAccounts() -> Bool {
        if accounts.count >= appConstants.Account.maxAccounts {
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
