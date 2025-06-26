//
//  BottomTabBarViewModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 16/06/25.
//


import Foundation
import Combine

@MainActor
class BottomTabBarViewModel: ObservableObject {
    @Injector var feedService: FeedService
    @Injector var accountService: AccountService
    @Injector var notificationService: NotificationHelperService
    @Injector var logger: LoggerService
    
    @Published var selectedTab: BottomTab = .dash
    @Published var showSettingsBadge: Bool = false
    @Published var showAppSync: Bool = false
    @Published var showTabBar: Bool = true
    
    /// A set to hold Combine cancellables for this view model.
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: Strings
    private let alertLang = AlertStrings.self.ExpiredUserLogOutAlert
    private let loaderLang = LoaderStrings.self
    
    private let tag = "BottomTabBarViewModel"
    
    init() {
        accountService.$activeAccount
            .sink { [weak self] account in
                guard let self else { return }
                if let account, account.isExpired == true {
                    self.presentExpiredAccountAlert(for: account)
                    return
                }
            }
            .store(in: &accountService.cancellables)
        
        self.showSettingsBadge = feedService.getUnreadFeedCount() > 0
        // TODO: Update the app sync display based on the app sync scale defined in the paired scale list
    }
    
    // MARK: - Tab Deactivation Handling
    /// A dictionary holding async deactivation handlers for each tab. A handler should return `true` if it is safe
    /// to leave the current tab, or `false` to cancel navigation. Views are responsible for registering and removing
    /// their own handlers via `registerDeactivationHandler` / `removeDeactivationHandler`.
    private var deactivationHandlers: [BottomTab: () async -> Bool] = [:]

    /// Registers a de-activation handler for the given tab, overriding any existing handler.
    /// - Parameters:
    ///   - tab:     The `BottomTab` for which the handler applies.
    ///   - handler: An async closure returning a `Bool` indicating whether the tab can be left.
    func registerDeactivationHandler(for tab: BottomTab, handler: @escaping () async -> Bool) {
        deactivationHandlers[tab] = handler
    }

    /// Removes any previously registered de-activation handler for the given tab.
    /// - Parameter tab: The `BottomTab` whose handler should be removed.
    func removeDeactivationHandler(for tab: BottomTab) {
        deactivationHandlers.removeValue(forKey: tab)
    }

    /// Returns the currently registered de-activation handler for a tab, if available.
    /// - Parameter tab: The tab whose handler is requested.
    func deactivationHandler(for tab: BottomTab) -> (() async -> Bool)? {
        deactivationHandlers[tab]
    }
    
    var visibleTabs: [BottomTab] {
        var tabs: [BottomTab] = [.dash, .entry, .history, .settings]
        if showAppSync {
            tabs.append(.appsync)
        }
        return tabs
    }
    
    func selectTab(_ tab: BottomTab) {
        selectedTab = tab
    }
    
    // MARK: - Expired Account Handling
    private func presentExpiredAccountAlert(for account: Account) {
        let userName = "\(account.firstName ?? "")"
        let alert = AlertModel(
            title: alertLang.title(userName),
            message: alertLang.message,
            buttons: [
                AlertButtonModel(title: alertLang.okButton, type: .primary) { _ in
                    Task { [weak self] in
                        guard let self else { return }
                        notificationService.showLoader(LoaderModel(text: loaderLang.loading))
                        do {
                            try await self.accountService.logOut(accountId: account.accountId)
                        } catch {
                            logger.log(
                                level: .error,
                                tag: tag,
                                message: "Failed to log out expired account",
                                data: error
                            )
                        }
                        notificationService.dismissLoader()
                    }
                }
            ]
        )
        notificationService.showAlert(alert)
    }

}
