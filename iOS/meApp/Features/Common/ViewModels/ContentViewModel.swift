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
    @Injector var bluetoothService: BluetoothService

    /// A set to hold Combine cancellables for this view model.
    private var cancellables = Set<AnyCancellable>()

    init() {
        accountService.$activeAccount
        // Treat re-logins of the *same* account as a new value so that the
        // loading flow gets a chance to run again. Comparing both the
        // accountId *and* lastActiveTime ensures that we still suppress
        // redundant emissions (e.g. when tokens refresh) while allowing a
        // fresh login to pass through.
            .removeDuplicates { lhs, rhs in
                lhs?.accountId == rhs?.accountId &&
                lhs?.lastActiveTime == rhs?.lastActiveTime
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
        try await entryService.loadDashboardData()
        bluetoothService.initialize()

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
