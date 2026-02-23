// LoadingScreenViewModel.swift
// meApp
//
// Created by Lakshmi Priya on 13/06/25.

import Combine
import Foundation
import UserNotifications

@MainActor
final class ContentViewModel: ObservableObject {
    @Published var isLoggedIn: Bool = false
    @Published var currentAccount: Account?
    /// Represents the current screen that should be visible in `ContentView`.
    @Published var contentViewState: ContentViewState = .initializing
    @Published var entries: [Entry] = []

    @Injector var accountService: AccountService
    @Injector var scaleService: ScaleService
    @Injector var feedService: FeedService
    @Injector var entryService: EntryService
    @Injector var logger: LoggerService
    @Injector var bluetoothService: BluetoothService
    @Injector var accountFlagService: AccountFlagService

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

        entryService.entrySaved
            .sink { [weak self] _ in
                guard let self else { return }
                Task {
                    await self.checkAccountFlagsAfterEntry()
                }
            }
            .store(in: &cancellables)
    }

    func performAppInitialization() {
        Task {
            contentViewState = .initializing
            let loggedIn = await checkLoginStatus()
            if loggedIn {
                // Refresh account data to sync weightless settings and other account data
                do {
                    try await accountService.refreshAccount()
                    logger.log(level: .info, tag: "ContentViewModel", message: "Account data refreshed successfully during initialization")
                } catch {
// swiftlint:disable:next line_length
                    logger.log(level: .error, tag: "ContentViewModel", message: "Failed to refresh account data during initialization: \(error.localizedDescription). Using local cache.")
                }
                
                // Capture dependencies to use off the main actor
                let entryService = self.entryService
                let feedService = self.feedService
                let bluetoothService = self.bluetoothService

                await entryService.syncAllEntriesWithRemote()
                await entryService.loadDashboardData()
                let entries = (try? await entryService.getAllEntries()) ?? []
                await feedService.fetchFeedItems()

                // UI-affecting calls back on main actor
                self.entries = entries
                bluetoothService.initialize()
                feedService.checkAndTriggerFeedModal()
                
                // Sync scales with remote server to ensure all previously saved scales are loaded
                await scaleService.syncAllScalesWithRemote()
                
                await self.checkAccountFlagsAfterLogin()
            }

            let afterUpdate = await checkLoginStatus()
            await updateViewState(isLoggedIn: afterUpdate)
            
            // Start Bluetooth operations after dashboard is ready
            if afterUpdate {
                await bluetoothService.startBluetoothOperations()
            }

            // Run migration in background so it doesn't block first-frame rendering
            let entryService = self.entryService
            Task(priority: .utility) {
                await entryService.migrateFromSQLiteIfNeeded()
            }
        }
    }

    // MARK: - Login Status Check
    private func checkLoginStatus() async -> Bool {
        do {
            try await accountService.updatePublishedState()
            currentAccount = accountService.activeAccount
            // Ensure theme is set for current account
            if let accountId = currentAccount?.accountId {
                Theme.shared.setActiveAccount(accountId)
            }
        } catch {
            currentAccount = nil
        }
        isLoggedIn = (currentAccount != nil)
        return isLoggedIn
    }

    // MARK: - Data Loading (if logged in)
    private func loadData() async {
// swiftlint:disable:next unused_optional_binding
        guard let _ = currentAccount else { return }
        await entryService.syncAllEntriesWithRemote()
        await entryService.loadDashboardData()
        bluetoothService.initialize()

        do {
            entries = try await entryService.getAllEntries()
        } catch {
            entries = []
        }
        await feedService.fetchFeedItems()
        feedService.checkAndTriggerFeedModal()
        
        // Sync scales with remote server to ensure all previously saved scales are loaded
        await scaleService.syncAllScalesWithRemote()
        
        await checkAccountFlagsAfterLogin()
    }

    // MARK: - View State Management
    func updateViewState(isLoggedIn: Bool) async {
        contentViewState = isLoggedIn ? .dashboard : .landing
    }
    
    // MARK: - Account Flags
    
    /// Shared function to check account flags for different triggers
    /// - Parameter trigger: The trigger type ("login" or "entry")
    private func checkAccountFlags(trigger: String) async {
        do {
            logger.log(level: .info, tag: "ContentViewModel", message: "Starting account flag check after \(trigger)")
            
            let flag = try await accountFlagService.getAccountFlag()
            if flag != nil {
                try await Task.sleep(nanoseconds: UInt64(AppConstants.TimeoutsAndRetention.appReviewTriggerTimeout))
                
                let flagProcessed = try await accountFlagService.checkAccountFlag(trigger: trigger)
                if flagProcessed {
                    logger.log(level: .info, tag: "ContentViewModel", message: "Account flag processed successfully after \(trigger)")
                } else {
                    logger.log(level: .debug, tag: "ContentViewModel", message: "No matching account flag found for \(trigger) trigger")
                }
            } else {
                logger.log(level: .debug, tag: "ContentViewModel", message: "No account flags found after \(trigger)")
            }
        } catch {
// swiftlint:disable:next line_length
            logger.log(level: .error, tag: "ContentViewModel", message: "Error checking account flags after \(trigger): \(error.localizedDescription)")
        }
    }
    
    /// Checks for account flags after successful login
    private func checkAccountFlagsAfterLogin() async {
        await checkAccountFlags(trigger: "login")
    }
    
    /// Checks for account flags after entry creation
    private func checkAccountFlagsAfterEntry() async {
        await checkAccountFlags(trigger: "entry")
    }
}
