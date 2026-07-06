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
    @Published var currentAccount: AccountSnapshot?
    /// Represents the current screen that should be visible in `ContentView`.
    @Published var contentViewState: ContentViewState = .initializing
    @Published var entries: [EntrySnapshot] = []

    @Injector var accountService: AccountServiceProtocol
    @Injector var deviceService: PairedDeviceServiceProtocol
    @Injector var feedService: FeedServiceProtocol
    @Injector var entryService: EntryServiceProtocol
    @Injector var logger: LoggerServiceProtocol
    @Injector var bluetoothService: BluetoothServiceProtocol
    @Injector var accountFlagService: AccountFlagServiceProtocol
    @Injector var notificationService: NotificationHelperServiceProtocol
    
    /// A set to hold Combine cancellables for this view model.
    private var cancellables = Set<AnyCancellable>()
    private(set) var initializationTask: Task<Void, Never>?
    /// The behind-the-dashboard remote sync task (MOB-1433). Exposed so tests
    /// can await it deterministically; production treats it as fire-and-forget.
    private(set) var backgroundSyncTask: Task<Void, Never>?
    private let tag = "ContentViewModel"
    private var lastHandledAccountSignature: AccountActivationSignature?
    private var queuedAccountSignatureWhileInitializing: AccountActivationSignature?

    // swiftlint:disable:next cyclomatic_complexity
    init( // swiftlint:disable:this function_body_length
        accountService: AccountServiceProtocol? = nil,
        deviceService: PairedDeviceServiceProtocol? = nil,
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
        if let deviceService {
            self.deviceService = deviceService
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

                let previousSignature = self.lastHandledAccountSignature
                let signature = AccountActivationSignature(
                    accountId: account?.accountId,
                    lastActiveTime: account?.lastActiveTime
                )

                // Suppress duplicate emissions for the same logical account/login event.
                if previousSignature == signature {
                    self.currentAccount = account
                    self.isLoggedIn = (account != nil)
                    return
                }

                self.lastHandledAccountSignature = signature
                self.currentAccount = account
                self.isLoggedIn = (account != nil)

                // MOB-1433: re-run full initialization (migrations + remote sync +
                // full reads) ONLY when the active account actually changes
                // (login / logout / switch). A `lastActiveTime`-only change — e.g.
                // the app returning to the foreground — refreshes the published
                // metadata above but must not re-run init; doing so re-synced the
                // whole history and stuttered the UI on every foreground.
                guard previousSignature?.accountId != signature.accountId else { return }

                // Avoid kicking off initialization from the publisher's initial emission
                // while the view model is still in its startup state. If initialization is
                // already running, queue a follow-up pass so newer account metadata is not dropped.
                guard self.contentViewState != .initializing else {
                    if self.initializationTask != nil {
                        self.queuedAccountSignatureWhileInitializing = signature
                    }
                    return
                }

                // Account was created mid-signup — hold off on dashboard navigation until
                // the signup flow explicitly clears this flag via finishSignup/completeSignup.
                guard !self.accountService.isSignupInProgress else { return }

                self.performAppInitialization()
            }
            .store(in: &cancellables)

        self.accountService.isSignupInProgressPublisher
            .dropFirst()
            .sink { [weak self] inProgress in
                guard let self, !inProgress, self.isLoggedIn else { return }
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
        backgroundSyncTask?.cancel()
        initializationTask = Task { @MainActor [weak self] in
            await self?.runAppInitialization()
        }
    }

    func waitForInitialization() async {
        await initializationTask?.value
    }

    /// Awaits the behind-the-dashboard remote sync. Test-only synchronization
    /// point; production never needs to block on it.
    func waitForBackgroundSync() async {
        await backgroundSyncTask?.value
    }

    // swiftlint:disable:next cyclomatic_complexity function_body_length
    private func runAppInitialization() async {
        // Clear any lingering loader state from previous session (e.g., if app was force-closed during account switch)
        notificationService.dismissLoader()
        // Stops any ongoing Bluetooth scan to prevent scan events from triggering alerts during the loading screen.
        bluetoothService.stopScan()

        logger.log(level: .info, tag: tag, message: "App initialization started")
        // Show loading only on first launch/landing; skip for metadata-triggered re-inits to avoid UI flicker.
        // MOB-196: while the loading screen is up, hold back any alert (permissions, sync errors,
        // goal prompts) so it doesn't appear over it; it's presented once loading completes below.
        let isShowingLoadingScreen = contentViewState != .dashboard
        if isShowingLoadingScreen {
            contentViewState = .initializing
            notificationService.setAppLoading(true)
        }
        // MOB-196: guarantee the loading flag is cleared on EVERY exit path that set it,
        // including the `guard !Task.isCancelled else { return }` early returns below. Without this,
        // a cancelled init leaves isAppLoading == true and silently suppresses every future alert
        // (the next re-run skips this branch once state is already .dashboard, so it never clears).
        // The explicit clear after updateViewState still drives the happy-path flush timing
        // (before Bluetooth startup); this defer is the idempotent safety net for cancelled paths.
        defer {
            if isShowingLoadingScreen {
                notificationService.setAppLoading(false)
            }
        }
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
                try await accountService.refreshAccount()
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
        // MOB-196: loading screen is dismissed now — allow alerts again and flush any that
        // were raised while it was up.
        if isShowingLoadingScreen {
            notificationService.setAppLoading(false)
        }
        logger.log(level: .info, tag: tag, message: "App initialization completed. isLoggedIn=\(afterUpdate), state=\(contentViewState)")
        
        // Ensure loader is dismissed after initialization completes (safety mechanism)
        notificationService.dismissLoader()
        
        // Start Bluetooth operations after dashboard is ready
        if afterUpdate {
            await bluetoothService.startBluetoothOperations()
            logger.log(level: .info, tag: tag, message: "Bluetooth operations started after initialization")
            // MOB-1433: kick off the remote sync + feed/scale refresh behind the
            // now-visible dashboard so the loading screen is never gated on it.
            startBackgroundDataSync()
        }

        // Re-initialize only on account change; ignore metadata-only updates.
        if let queued = queuedAccountSignatureWhileInitializing {
            queuedAccountSignatureWhileInitializing = nil
            let currentAccountId = currentAccount?.accountId
            if queued.accountId != currentAccountId {
                performAppInitialization()
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
            logger.log(level: .error, tag: tag, message: "Failed to update login status from local account state")
        }
        isLoggedIn = (currentAccount != nil)
        return isLoggedIn
    }

    // MARK: - Data Loading (if logged in)

    /// Local-only work that must finish before the dashboard is shown: one-time
    /// migrations, local aggregation, and the local snapshot read. The remote
    /// sync — the long pole on a 5k–10k-entry account — is deferred to
    /// `startBackgroundDataSync()` so it never gates the loading screen (MOB-1433).
    private func loadData() async {
        guard currentAccount != nil else { return }
        // Migration runs before the local read so opStack entries are present.
        await entryService.migrateFromSQLiteIfNeeded()
        await entryService.migrateBabyEntriesToDecigrams()
        // Aggregate + read from whatever is already local so the dashboard has
        // data to show immediately.
        await entryService.loadDashboardData(entryType: .scale)
        bluetoothService.initialize()

        do {
            entries = try await entryService.fetchAllEntrySnapshots()
        } catch {
            entries = []
        }

        logger.log(level: .info, tag: tag, message: "Initialization loaded local entries. count=\(entries.count)")
    }

    /// Remote sync + feed/scale refresh, run behind the dashboard so a large
    /// first-history sync never blocks the loading screen. `EntryService.isSyncing`
    /// drives any in-UI sync indicator; the merge's own publishers refresh the
    /// dashboard/history stores as data arrives, and we re-read the local
    /// snapshots once at the end so `entries` reflects the synced set.
    private func startBackgroundDataSync() {
        backgroundSyncTask = Task { [weak self] in
            guard let self else { return }
            await self.entryService.syncAllEntriesWithRemote()
            await self.entryService.loadDashboardData(entryType: .scale)
            do {
                self.entries = try await self.entryService.fetchAllEntrySnapshots()
            } catch {
                // Keep the locally-loaded entries on a sync/read failure.
            }
            await self.feedService.fetchFeedItems()
            self.feedService.checkAndTriggerFeedModal()

            // Sync scales with remote so all previously saved scales are loaded.
            await self.deviceService.syncAllScalesWithRemote()

            self.logger.log(
                level: .info,
                tag: self.tag,
                message: "Background data sync completed. entries=\(self.entries.count)"
            )
            await self.checkAccountFlagsAfterLogin()
        }
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
