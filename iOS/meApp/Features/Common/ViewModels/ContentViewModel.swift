// LoadingScreenViewModel.swift
// meApp
//
// Created by Lakshmi Priya on 13/06/25.

import Foundation
import Combine
import UserNotifications

@MainActor
final class ContentViewModel: ObservableObject {
    @Published var isLoggedIn: Bool = false
    @Published var currentAccount: Account?
    /// Represents the current screen that should be visible in `ContentView`.
    @Published var contentViewState: ContentViewState = .initializing
    @Published var entries: [Entry] = []
    
    @Injector var accountService: AccountService
    @Injector var scaleService : ScaleService
    @Injector var feedService : FeedService
    @Injector var entryService : EntryService
    @Injector var logger : LoggerService
    @Injector var pushNotificationService : PushNotificationService
    
    /// A set to hold Combine cancellables for this view model.
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        accountService.$activeAccount
        // Avoid unnecessary re-initialisation when the account ID hasn't changed
            .removeDuplicates { lhs, rhs in
                lhs?.accountId == rhs?.accountId
            }
            .sink { [weak self] account in
                guard let self else { return }
                self.currentAccount = account
                self.isLoggedIn = (account != nil)
                
                // Prevent recursive calls while an initialisation cycle is already running
                guard self.contentViewState != .initializing else { return }
                
                self.performAppInitialization()
            }
            .store(in: &cancellables)
    }
    
    func performAppInitialization() {
        Task {
            contentViewState = .initializing
            let loggedIn = await checkLoginStatus()
            if loggedIn {
                // Check if notifications are required for the current account/scales
                let notificationsRequired = await areNotificationsRequired()
                if notificationsRequired {
                    await pushNotificationService.setupPushNotifications()
                } else {
                    await pushNotificationService.updateDeviceInfo()
                }
                await loadData()
            }
            let afterUpdate = await checkLoginStatus()
            await updateViewState(isLoggedIn: afterUpdate)
        }
    }
    
    // MARK: - Login Status Check
    private func checkLoginStatus() async -> Bool {
        do {
            try await accountService.updatePublishedState()
            currentAccount = accountService.activeAccount
        } catch {
            currentAccount = nil
        }
        isLoggedIn = (currentAccount != nil)
        return isLoggedIn
    }
    
    // MARK: - Data Loading (if logged in)
    private func loadData() async {
        await scaleService.syncAllScalesWithRemote()
        guard let _ = currentAccount else { return }
        await entryService.syncAllEntriesWithRemote()
        
        do {
            entries = try await entryService.getAllEntries()
        } catch {
            entries = []
        }
        do {
            try await feedService.fetchFeedItems()
        } catch {
            logger.log(level: .error, tag: "ContentViewModel", message: "Failed to fetch feed items: \(error)")
        }
    }
    
    // MARK: - View State Management
    func updateViewState(isLoggedIn: Bool) async {
        contentViewState = isLoggedIn ? .dashboard : .landing
    }
    
    // MARK: - Notification Permission Logic
    private func areNotificationsRequired() async -> Bool {
        await pushNotificationService.isNotificationAuthorized()
    }
}
