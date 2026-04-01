// ContentViewModel.swift
// meApp
//
// Created by Lakshmi Priya on 13/06/25.

import Combine
import Foundation
import UserNotifications

@MainActor
final class ContentViewModel: ObservableObject {
    private struct AccountActivationSignature: Equatable {
        let accountId: String?
        let lastActiveTime: String?
    }

    @Published var isLoggedIn: Bool = false
    @Published var currentAccount: Account?
    /// Represents the current screen that should be visible in `ContentView`.
    @Published var contentViewState: ContentViewState = .initializing
    @Published var entries: [Entry] = []

    @Injector var accountService: AccountServiceProtocol
    @Injector var scaleService: ScaleServiceProtocol
    @Injector var feedService: FeedServiceProtocol
    @Injector var entryService: EntryServiceProtocol
    @Injector var logger: LoggerServiceProtocol
    @Injector var bluetoothService: BluetoothServiceProtocol
    @Injector var accountFlagService: AccountFlagServiceProtocol
    @Injector var notificationService: NotificationHelperServiceProtocol
    
    /// A set to hold Combine cancellables for this view model.
    private var cancellables = Set<AnyCancellable>()
    private(set) var initializationTask: Task<Void, Never>?
    private let tag = "ContentViewModel"
    private var lastHandledAccountSignature: AccountActivationSignature?
    private var queuedAccountSignatureWhileInitializing: AccountActivationSignature?

    // swiftlint:disable:next cyclomatic_complexity
    init( // swiftlint:disable:this function_body_length
        accountService: AccountServiceProtocol? = nil,
        scaleService: ScaleServiceProtocol? = nil,
        feedService: FeedServiceProtocol? = nil,
        entryService: EntryServiceProtocol? = nil,
        logger: LoggerServiceProtocol? = nil,
        bluetoothService: BluetoothServiceProtocol? = nil,
        accountFlagService: AccountFlagServiceProtocol? = nil,
        notificationService: NotificationHelperServiceProtocol? = nil
    ) {
        if let accountService {
            self.accountService = accountService
        }
        if let scaleService {
            self.scaleService = scaleService
        }
        if let feedService {
            self.feedService = feedService
        }
        if let entryService {
            self.entryService = entryService
        }
        if let logger {
            self.logger = logger
        }
        if let bluetoothService {
            self.bluetoothService = bluetoothService
        }
        if let accountFlagService {
            self.accountFlagService = accountFlagService
        }
        if let notificationService {
            self.notificationService = notificationService
        }

        self.accountService.activeAccountPublisher
            .sink { [weak self] account in
                guard let self else { return }

                let signature = AccountActivationSignature(
                    accountId: account?.accountId,
                    lastActiveTime: account?.lastActiveTime
                )

                // Suppress duplicate emissions for the same logical account/login event,
                // but allow the same account to retrigger initialization when
                // `lastActiveTime` changes.
                if self.lastHandledAccountSignature == signature {
                    self.currentAccount = account
                    self.isLoggedIn = (account != nil)
                    return
                }

                self.lastHandledAccountSignature = signature
                self.currentAccount = account
                self.isLoggedIn = (account != nil)

                // Avoid kicking off initialization from the publisher's initial emission
                // while the view model is still in its startup state. If initialization is
                // already running, queue a follow-up pass so newer account metadata is not dropped.
                guard self.contentViewState != .initializing else {
                    if self.initializationTask != nil {
                        self.queuedAccountSignatureWhileInitializing = signature
                    }
                    return
                }

                self.performAppInitialization()
            }
            .store(in: &cancellables)

        self.entryService.entrySaved
            .sink { [weak self] _ in
                guard let self else { return }
                Task {
                    await self.checkAccountFlagsAfterEntry()
                }
            }
            .store(in: &cancellables)
    }

    func performAppInitialization() {
        initializationTask?.cancel()
        initializationTask = Task { @MainActor [weak self] in
            await self?.runAppInitialization()
        }
    }

    func waitForInitialization() async {
        await initializationTask?.value
    }

    // swiftlint:disable:next cyclomatic_complexity
    private func runAppInitialization() async {
        // Clear any lingering loader state from previous session (e.g., if app was force-closed during account switch)
        notificationService.dismissLoader()
        
        logger.log(level: .info, tag: tag, message: "App initialization started")
        contentViewState = .initializing
        var loggedIn = await checkLoginStatus()
        guard !Task.isCancelled else { return }

        if !loggedIn {
            await waitForStartupMigrationIfNeeded()
            guard !Task.isCancelled else { return }
            loggedIn = await checkLoginStatus()
            guard !Task.isCancelled else { return }
        }

        if loggedIn {
            // Refresh account data to sync weightless settings and other account data
            do {
                _ = try await accountService.refreshAccount()
                logger.log(level: .info, tag: tag, message: "Account data refreshed successfully during initialization")
            } catch {
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "Failed to refresh account data during initialization: \(error.localizedDescription). Using local cache."
                )
            }
            guard !Task.isCancelled else { return }
            await loadData()
            guard !Task.isCancelled else { return }
        }

        let afterUpdate = await checkLoginStatus()
        guard !Task.isCancelled else { return }
        await updateViewState(isLoggedIn: afterUpdate)
        logger.log(level: .info, tag: tag, message: "App initialization completed. isLoggedIn=\(afterUpdate), state=\(contentViewState)")
        
        // Ensure loader is dismissed after initialization completes (safety mechanism)
        notificationService.dismissLoader()
        
        // Start Bluetooth operations after dashboard is ready
        if afterUpdate {
            await bluetoothService.startBluetoothOperations()
            logger.log(level: .info, tag: tag, message: "Bluetooth operations started after initialization")
        }

        if queuedAccountSignatureWhileInitializing != nil {
            queuedAccountSignatureWhileInitializing = nil
            performAppInitialization()
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
            logger.log(level: .error, tag: tag, message: "Failed to update login status from local account state")
        }
        isLoggedIn = (currentAccount != nil)
        return isLoggedIn
    }

    // MARK: - Data Loading (if logged in)

    private func loadData() async {
        // swiftlint:disable:next unused_optional_binding
        guard let _ = currentAccount else { return }
        // Migration runs before sync so opStack entries are available for first sync.
        await entryService.migrateFromSQLiteIfNeeded()
        await entryService.migrateBabyEntriesToDecigrams()
        await entryService.syncAllEntriesWithRemote()
        await entryService.loadDashboardData(entryType: .wg)
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

        logger.log(level: .info, tag: tag, message: "Initialization loaded entries. count=\(entries.count)")
        await checkAccountFlagsAfterLogin()
    }

    // MARK: - View State Management

    func updateViewState(isLoggedIn: Bool) async {
        contentViewState = isLoggedIn ? .dashboard : .landing
        logger.log(level: .info, tag: tag, message: "Updated content view state. isLoggedIn=\(isLoggedIn), state=\(contentViewState)")
    }

    private func waitForStartupMigrationIfNeeded() async {
        guard accountService.shouldDeferUnauthenticatedLanding() else { return }

        let timeoutNanos: UInt64 = 15_000_000_000
        let intervalNanos: UInt64 = 300_000_000
        let start = DispatchTime.now().uptimeNanoseconds

        while accountService.shouldDeferUnauthenticatedLanding() {
            if Task.isCancelled {
                return
            }
            let now = DispatchTime.now().uptimeNanoseconds
            if now - start >= timeoutNanos {
                logger.log(
                    level: .info,
                    tag: tag,
                    message: "Migration wait timed out (\(timeoutNanos / 1_000_000_000)s); continuing."
                )
                break
            }
            do {
                try await Task.sleep(nanoseconds: intervalNanos)
            } catch is CancellationError {
                return
            } catch {
                // Ignore unexpected sleep errors and continue timeout-controlled polling.
            }
        }
    }
    
    // MARK: - Account Flags

    /// Shared function to check account flags for different triggers
    /// - Parameter trigger: The trigger type ("login" or "entry")
    private func checkAccountFlags(trigger: String) async {
        do {
            let flag = try await accountFlagService.getAccountFlag()
            if flag != nil {
                try await Task.sleep(nanoseconds: UInt64(AppConstants.TimeoutsAndRetention.appReviewTriggerTimeout))

                let flagProcessed = try await accountFlagService.checkAccountFlag(trigger: trigger)
                if flagProcessed {
                    logger.log(level: .info, tag: tag, message: "Account flag processed successfully after \(trigger)")
                } else {
                    logger.log(level: .debug, tag: tag, message: "No matching account flag found for \(trigger) trigger")
                }
            } else {
                logger.log(level: .debug, tag: tag, message: "No account flags found after \(trigger)")
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Error checking account flags after \(trigger): \(error.localizedDescription)")
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
