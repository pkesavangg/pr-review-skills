import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct ContentViewModelTests {
    @Test("performAppInitialization logged-out path ends on landing without startup data loading")
    func initializationLoggedOutPath() async {
        let (viewModel, account, _, entry, feed, scale, bluetooth, accountFlag) = makeSUT()
        account.activeAccount = nil

        viewModel.performAppInitialization()
        await viewModel.waitForInitialization()

        #expect(viewModel.contentViewState == .landing)
        #expect(viewModel.isLoggedIn == false)
        #expect(viewModel.currentAccount == nil)
        #expect(entry.syncAllEntriesCalls == 0)
        #expect(feed.fetchFeedItemsCalls == 0)
        #expect(scale.syncAllScalesWithRemoteCalls == 0)
        #expect(bluetooth.startBluetoothOperationsCalls == 0)
        #expect(accountFlag.getAccountFlagCalls == 0)
    }

    @Test("performAppInitialization logged-in path loads startup data and ends on dashboard")
    func initializationLoggedInPathLoadsStartupData() async {
        let (viewModel, account, _, entry, feed, scale, bluetooth, accountFlag) = makeSUT()
        account.activeAccount = ContentViewModelTestFixtures.makeActiveAccount(id: "content-1", lastActiveTime: "t1")
        account.refreshAccountResult = .success(())
        entry.allEntrySnapshotsResult = .success(ContentViewModelTestFixtures.makeEntrySnapshots(accountId: "content-1", count: 2))

        viewModel.performAppInitialization()
        await viewModel.waitForInitialization()
        // MOB-1433: the dashboard shows after the LOCAL load; remote sync + feed
        // + scale refresh run behind it. Await that background pass before
        // asserting on its effects.
        await viewModel.waitForBackgroundSync()

        #expect(viewModel.contentViewState == .dashboard)
        #expect(bluetooth.startBluetoothOperationsCalls == 1)
        #expect(account.refreshAccountCalls == 1)
        #expect(entry.migrateFromSQLiteCalls == 1)
        #expect(entry.syncAllEntriesCalls == 1)
        // Dashboard aggregation now runs behind the dashboard (startBackgroundDataSync),
        // once — NOT on the loading-screen critical path. The unused entries-snapshot read
        // is dropped entirely (no view consumes ContentViewModel.entries) (MOB-1433 §5c).
        #expect(entry.loadDashboardDataCalls == 1)
        #expect(entry.fetchAllEntrySnapshotsCalls == 0)
        #expect(feed.fetchFeedItemsCalls == 1)
        #expect(feed.checkAndTriggerFeedModalCalls == 1)
        #expect(scale.syncAllScalesWithRemoteCalls == 1)
        #expect(bluetooth.initializeCalls == 1)
        #expect(accountFlag.getAccountFlagCalls == 1)
    }

    @Test("performAppInitialization continues to dashboard when account refresh fails")
    func initializationRefreshFailureStillLoadsData() async {
        let (viewModel, account, _, entry, feed, scale, bluetooth, _) = makeSUT()
        account.activeAccount = ContentViewModelTestFixtures.makeActiveAccount(id: "content-2")
        account.refreshAccountResult = .failure(ContentViewModelTestError.refreshFailed)
        entry.allEntriesResult = .success(ContentViewModelTestFixtures.makeEntries(accountId: "content-2", count: 1))

        viewModel.performAppInitialization()
        await viewModel.waitForInitialization()
        await viewModel.waitForBackgroundSync()

        #expect(viewModel.contentViewState == .dashboard)
        #expect(account.refreshAccountCalls == 1)
        #expect(entry.syncAllEntriesCalls == 1)
        #expect(feed.fetchFeedItemsCalls == 1)
        #expect(scale.syncAllScalesWithRemoteCalls == 1)
        #expect(bluetooth.startBluetoothOperationsCalls == 1)
    }

    @Test("performAppInitialization handles startup sync/data fetch failure with empty entries")
    func initializationEntryFetchFailure() async {
        let (viewModel, account, _, entry, _, _, _, _) = makeSUT()
        account.activeAccount = ContentViewModelTestFixtures.makeActiveAccount(id: "content-3")
        account.refreshAccountResult = .success(())
        entry.allEntriesResult = .failure(ContentViewModelTestError.fetchEntriesFailed)

        viewModel.performAppInitialization()
        await viewModel.waitForInitialization()

        #expect(viewModel.contentViewState == .dashboard)
        #expect(viewModel.entries.isEmpty == true)
    }

    @Test("performAppInitialization handles account updatePublishedState error and lands logged-out")
    func initializationUpdateStateFailure() async {
        let (viewModel, account, _, entry, _, _, bluetooth, _) = makeSUT()
        account.activeAccount = ContentViewModelTestFixtures.makeActiveAccount(id: "content-4")
        account.updatePublishedStateError = ContentViewModelTestError.updateStateFailed

        viewModel.performAppInitialization()
        await viewModel.waitForInitialization()

        #expect(viewModel.contentViewState == .landing)
        #expect(viewModel.isLoggedIn == false)
        #expect(viewModel.currentAccount == nil)
        #expect(account.refreshAccountCalls == 0)
        #expect(entry.syncAllEntriesCalls == 0)
        #expect(bluetooth.startBluetoothOperationsCalls == 0)
    }

    @Test("performAppInitialization waits for startup migration defer and then resolves to landing")
    func initializationWaitsForDeferredLanding() async {
        let (viewModel, account, _, _, _, _, _, _) = makeSUT()
        account.activeAccount = nil
        account.shouldDeferUnauthenticatedLandingResult = true

        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 150_000_000)
            account.shouldDeferUnauthenticatedLandingResult = false
        }

        viewModel.performAppInitialization()
        await viewModel.waitForInitialization()

        #expect(viewModel.contentViewState == .landing)
        #expect(account.updatePublishedStateCalls >= 2)
    }

    @Test("account publisher triggers re-initialization when state is no longer initializing")
    func accountPublisherTriggersInitialization() async {
        let (viewModel, account, _, entry, _, _, _, _) = makeSUT()
        viewModel.contentViewState = .landing
        account.refreshAccountResult = .success(())
        entry.allEntriesResult = .success(ContentViewModelTestFixtures.makeEntries(accountId: "content-5", count: 1))

        account.activeAccount = ContentViewModelTestFixtures.makeActiveAccount(id: "content-5", lastActiveTime: "t1")
        let updated = await waitUntil { viewModel.contentViewState == .dashboard }

        #expect(updated == true)
        #expect(account.refreshAccountCalls == 1)
    }

    @Test("account publisher does not recurse while already initializing")
    func accountPublisherNoRecursionWhileInitializing() async {
        let (viewModel, account, _, _, _, _, _, _) = makeSUT()
        #expect(viewModel.contentViewState == .initializing)

        account.activeAccount = ContentViewModelTestFixtures.makeActiveAccount(id: "content-6", lastActiveTime: "t1")
        let stable = await waitUntil(timeoutNanoseconds: 200_000_000) { account.refreshAccountCalls == 0 }

        #expect(stable == true)
        #expect(account.refreshAccountCalls == 0)
    }

    @Test("account publisher ignores duplicate account with same lastActiveTime")
    func accountPublisherIgnoresDuplicates() async {
        let (viewModel, account, _, entry, _, _, _, _) = makeSUT()
        viewModel.contentViewState = .landing
        account.refreshAccountResult = .success(())
        entry.allEntriesResult = .success(ContentViewModelTestFixtures.makeEntries(accountId: "content-7", count: 1))

        account.activeAccount = ContentViewModelTestFixtures.makeActiveAccount(id: "content-7", lastActiveTime: "t1")
        _ = await waitUntil { account.refreshAccountCalls == 1 }

        viewModel.contentViewState = .landing
        account.activeAccount = ContentViewModelTestFixtures.makeActiveAccount(id: "content-7", lastActiveTime: "t1")
        _ = await waitUntil(timeoutNanoseconds: 200_000_000) { true }

        #expect(account.refreshAccountCalls == 1)
    }

    @Test("account publisher does NOT re-initialize on a lastActiveTime-only change; refreshes metadata only")
    func accountPublisherSkipsReinitOnNewLastActiveTime() async {
        let (viewModel, account, _, entry, _, _, _, _) = makeSUT()
        viewModel.contentViewState = .landing
        account.refreshAccountResult = .success(())
        entry.allEntriesResult = .success(ContentViewModelTestFixtures.makeEntries(accountId: "content-8", count: 1))

        // First activation of a new account → full init runs.
        account.activeAccount = ContentViewModelTestFixtures.makeActiveAccount(id: "content-8", lastActiveTime: "t1")
        _ = await waitUntil { account.refreshAccountCalls == 1 }
        await viewModel.waitForInitialization()
        await viewModel.waitForBackgroundSync()

        // Same account, new lastActiveTime (e.g. app foregrounded). MOB-1433:
        // this must refresh the published metadata but NOT re-run initialization
        // (which previously re-synced the whole history and stuttered the UI).
        viewModel.contentViewState = .landing
        account.activeAccount = ContentViewModelTestFixtures.makeActiveAccount(id: "content-8", lastActiveTime: "t2")
        _ = await waitUntil(timeoutNanoseconds: 300_000_000) { viewModel.currentAccount?.lastActiveTime == "t2" }

        #expect(account.refreshAccountCalls == 1)                  // no second init
        #expect(viewModel.currentAccount?.lastActiveTime == "t2")  // metadata refreshed
        #expect(viewModel.contentViewState == .landing)            // untouched by the metadata-only change
    }

    @Test("entry saved publisher triggers account-flag check for entry flow")
    func entrySavedTriggersAccountFlagCheck() async {
        let (viewModel, _, _, entry, _, _, _, accountFlag) = makeSUT()
        _ = viewModel
        accountFlag.getAccountFlagResult = .success(nil)

        entry.entrySaved.send(ContentViewModelTestFixtures.makeEntryNotification())
        let checked = await waitUntil { accountFlag.getAccountFlagCalls == 1 }

        #expect(checked == true)
        #expect(accountFlag.checkAccountFlagCalls == 0)
    }

    @Test("entry saved publisher processes account flag with entry trigger when flag exists")
    func entrySavedProcessesAccountFlagWithEntryTrigger() async {
        let (viewModel, _, _, entry, _, _, _, accountFlag) = makeSUT()
        _ = viewModel
        accountFlag.getAccountFlagResult = .success(ContentViewModelTestFixtures.makeAccountFlag(trigger: "entry"))
        accountFlag.checkAccountFlagResult = .success(true)

        entry.entrySaved.send(ContentViewModelTestFixtures.makeEntryNotification())
        let processed = await waitUntil(timeoutNanoseconds: 5_000_000_000) { accountFlag.checkAccountFlagCalls == 1 }

        #expect(processed == true)
        #expect(accountFlag.getAccountFlagCalls == 1)
        #expect(accountFlag.checkAccountFlagCalls == 1)
        #expect(accountFlag.lastCheckTrigger == "entry")
    }

    @Test("checkAccountFlagsAfterLogin processes account flag with login trigger when flag exists")
    func checkAccountFlagsAfterLoginProcessesFlag() async {
        let (viewModel, account, _, entry, _, _, _, accountFlag) = makeSUT()
        account.activeAccount = ContentViewModelTestFixtures.makeActiveAccount(id: "content-flag-1")
        account.refreshAccountResult = .success(())
        entry.allEntriesResult = .success(ContentViewModelTestFixtures.makeEntries(accountId: "content-flag-1", count: 1))
        accountFlag.getAccountFlagResult = .success(ContentViewModelTestFixtures.makeAccountFlag(trigger: "login"))
        accountFlag.checkAccountFlagResult = .success(true)

        viewModel.performAppInitialization()
        let processed = await waitUntil(timeoutNanoseconds: 3_000_000_000) {
            accountFlag.checkAccountFlagCalls == 1 && viewModel.contentViewState == .dashboard
        }

        #expect(processed == true)
        #expect(accountFlag.getAccountFlagCalls == 1)
        #expect(accountFlag.checkAccountFlagCalls == 1)
        #expect(accountFlag.lastCheckTrigger == "login")
    }

    @Test("checkAccountFlags handles getAccountFlag error gracefully")
    func checkAccountFlagsHandlesGetAccountFlagError() async {
        let (viewModel, account, _, entry, _, _, _, accountFlag) = makeSUT()
        account.activeAccount = ContentViewModelTestFixtures.makeActiveAccount(id: "content-flag-error-1")
        account.refreshAccountResult = .success(())
        entry.allEntriesResult = .success(ContentViewModelTestFixtures.makeEntries(accountId: "content-flag-error-1", count: 1))
        accountFlag.getAccountFlagResult = .failure(ContentViewModelTestError.accountFlagFailed)

        viewModel.performAppInitialization()
        // getAccountFlag now runs in the background sync (after the dashboard is
        // shown), so wait for that pass rather than just the dashboard flip.
        let completed = await waitUntil {
            viewModel.contentViewState == .dashboard && accountFlag.getAccountFlagCalls == 1
        }

        #expect(completed == true)
        #expect(accountFlag.getAccountFlagCalls == 1)
        #expect(accountFlag.checkAccountFlagCalls == 0)
    }

    @Test("waitForStartupMigrationIfNeeded times out after 15 seconds")
    func waitForStartupMigrationTimesOut() async {
        let (viewModel, account, _, _, _, _, _, _) = makeSUT()
        account.activeAccount = nil
        account.shouldDeferUnauthenticatedLandingResult = true

        viewModel.performAppInitialization()
        let timedOut = await waitUntil(timeoutNanoseconds: 16_000_000_000) { viewModel.contentViewState == .landing }

        #expect(timedOut == true)
        #expect(account.updatePublishedStateCalls >= 1)
    }

    @Test("updateViewState maps login status to final screen state")
    func updateViewStateMapsStates() async {
        let (viewModel, _, _, _, _, _, _, _) = makeSUT()

        await viewModel.updateViewState(isLoggedIn: false)
        #expect(viewModel.contentViewState == .landing)

        await viewModel.updateViewState(isLoggedIn: true)
        #expect(viewModel.contentViewState == .dashboard)
    }
}

@MainActor
private func makeSUT(
    accountService: MockAccountService? = nil,
    entryService: MockContentViewModelEntryService? = nil,
    feedService: MockContentViewModelFeedService? = nil,
    scaleService: MockContentViewModelScaleService? = nil,
    bluetoothService: MockContentViewModelBluetoothService? = nil,
    accountFlagService: MockContentViewModelAccountFlagService? = nil,
    loggerService: MockLoggerService? = nil
) -> ( // swiftlint:disable:this large_tuple
    viewModel: ContentViewModel,
    accountService: MockAccountService,
    loggerService: MockLoggerService,
    entryService: MockContentViewModelEntryService,
    feedService: MockContentViewModelFeedService,
    scaleService: MockContentViewModelScaleService,
    bluetoothService: MockContentViewModelBluetoothService,
    accountFlagService: MockContentViewModelAccountFlagService
) {
    let account = accountService ?? MockAccountService()
    let logger = loggerService ?? MockLoggerService()
    let entry = entryService ?? MockContentViewModelEntryService()
    let feed = feedService ?? MockContentViewModelFeedService()
    let scale = scaleService ?? MockContentViewModelScaleService()
    let bluetooth = bluetoothService ?? MockContentViewModelBluetoothService()
    let accountFlag = accountFlagService ?? MockContentViewModelAccountFlagService()
    let notification = MockNotificationHelperService()

    let viewModel = ContentViewModel(
        accountService: account,
        deviceService: scale,
        feedService: feed,
        entryService: entry,
        logger: logger,
        bluetoothService: bluetooth,
        accountFlagService: accountFlag,
        notificationService: notification
    )
    return (viewModel, account, logger, entry, feed, scale, bluetooth, accountFlag)
}

@MainActor
private func waitUntil(
    timeoutNanoseconds: UInt64 = 3_000_000_000,
    pollIntervalNanoseconds: UInt64 = 10_000_000,
    condition: @MainActor () -> Bool
) async -> Bool {
    let start = DispatchTime.now().uptimeNanoseconds
    while DispatchTime.now().uptimeNanoseconds - start < timeoutNanoseconds {
        if condition() { return true }
        try? await Task.sleep(nanoseconds: pollIntervalNanoseconds)
    }
    return false
}
