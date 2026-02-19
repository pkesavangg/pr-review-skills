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
    
    private let networkMonitor = NetworkMonitor.shared
    
    // MARK: Published State
    @Published var accounts: [Account] = []
    @Published var userItems: [UserItemInfo] = []
    
    let loadingLang = LoaderStrings.self
    private let alertStrings = AlertStrings.self
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
    private func setupAccountObservation() {
        accountService.$allAccounts
            .receive(on: DispatchQueue.main)
            .sink { [weak self] all in
                guard let self = self else { return }
                //Only show logged-in accounts
                let loggedInAccounts = all.filter { 
                    $0.isLoggedIn == true && ($0.isExpired ?? false) == false
                }
                
                // Sort logged-in accounts by last active time
                let sortedLoggedInAccounts = loggedInAccounts.sorted { lhs, rhs in
                    let lhsDate = DateTimeTools.parse(lhs.lastActiveTime ?? "") ?? .distantPast
                    let rhsDate = DateTimeTools.parse(rhs.lastActiveTime ?? "") ?? .distantPast
                    return lhsDate > rhsDate
                }
                
                self.accounts = sortedLoggedInAccounts
                
                self.userItems = sortedLoggedInAccounts.map { account in
                    return UserItemInfo(
                        accountID: account.accountId,
                        name: account.firstName?.isEmpty == false ? account.firstName! : account.email,
                        email: account.email,
                        isSelected: false,
                        isExpired: false, // Only logged-in accounts are shown
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
                try await accountService.switchAccount(to: account)
                notificationService.showToast(ToastModel(message: toastLang.switchingAccount(userName)))
                logger.log(level: .info, tag: tag, message: "Switched active account to \(accountID)")
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to switch account", data: error.localizedDescription)
                switch error {
                case HTTPError.noInternet:
                    notificationService.showToast(ToastModel(message: toastLang.unableToConnect))
                default:
                    notificationService.showToast(ToastModel(message: toastLang.somethingWentWrong))
                }
            }
        }
    }
    
    // MARK: Max Accounts Handling
    /// Returns `true` if another account can be added. If the maximum number of
    /// accounts has already been reached, shows an alert and returns `false`.
    func canAddMoreAccounts() -> Bool {
        // accounts array already contains only logged-in, non-expired accounts
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
