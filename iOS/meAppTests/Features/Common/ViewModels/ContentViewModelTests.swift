import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct ContentViewModelTests {
    @Test("performAppInitialization logged-out path ends on landing without startup data loading")
    func initializationLoggedOutPath() async {
        let (viewModel, account, _, entry, feed, scale, bluetooth, accountFlag) = makeSUT()
        account.activeAccount = nil

        viewModel.performAppInitialization()
        let updated = await waitUntil { viewModel.contentViewState == .landing }

        #expect(updated == true)
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
        account.refreshAccountResult = .success(ContentViewModelTestFixtures.makeActiveAccount(id: "content-1", lastActiveTime: "t1"))
        entry.allEntriesResult = .success(ContentViewModelTestFixtures.makeEntries(accountId: "content-1", count: 2))

        viewModel.performAppInitialization()
        let updated = await waitUntil { viewModel.contentViewState == .dashboard && bluetooth.startBluetoothOperationsCalls == 1 }

        #expect(updated == true)
        #expect(account.refreshAccountCalls == 1)
        #expect(entry.migrateFromSQLiteCalls == 1)
        #expect(entry.syncAllEntriesCalls == 1)
        #expect(entry.loadDashboardDataCalls == 1)
        #expect(entry.getAllEntriesCalls == 1)
        #expect(feed.fetchFeedItemsCalls == 1)
        #expect(feed.checkAndTriggerFeedModalCalls == 1)
        #expect(scale.syncAllScalesWithRemoteCalls == 1)
        #expect(bluetooth.initializeCalls == 1)
        #expect(viewModel.entries.count == 2)
        #expect(accountFlag.getAccountFlagCalls == 1)
    }

    @Test("performAppInitialization continues to dashboard when account refresh fails")
    func initializationRefreshFailureStillLoadsData() async {
        let (viewModel, account, _, entry, feed, scale, bluetooth, _) = makeSUT()
        account.activeAccount = ContentViewModelTestFixtures.makeActiveAccount(id: "content-2")
        account.refreshAccountResult = .failure(ContentViewModelTestError.refreshFailed)
        entry.allEntriesResult = .success(ContentViewModelTestFixtures.makeEntries(accountId: "content-2", count: 1))

        viewModel.performAppInitialization()
        let updated = await waitUntil { viewModel.contentViewState == .dashboard }

        #expect(updated == true)
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
        account.refreshAccountResult = .success(ContentViewModelTestFixtures.makeActiveAccount(id: "content-3"))
        entry.allEntriesResult = .failure(ContentViewModelTestError.fetchEntriesFailed)

        viewModel.performAppInitialization()
        let updated = await waitUntil { viewModel.contentViewState == .dashboard }

        #expect(updated == true)
        #expect(viewModel.entries.isEmpty == true)
    }

    @Test("performAppInitialization handles account updatePublishedState error and lands logged-out")
    func initializationUpdateStateFailure() async {
        let (viewModel, account, _, entry, _, _, bluetooth, _) = makeSUT()
        account.activeAccount = ContentViewModelTestFixtures.makeActiveAccount(id: "content-4")
        account.updatePublishedStateError = ContentViewModelTestError.updateStateFailed

        viewModel.performAppInitialization()
        let updated = await waitUntil { viewModel.contentViewState == .landing }

        #expect(updated == true)
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

        Task {
            try? await Task.sleep(nanoseconds: 350_000_000)
            account.shouldDeferUnauthenticatedLandingResult = false
        }

        viewModel.performAppInitialization()
        let updated = await waitUntil(timeoutNanoseconds: 3_000_000_000) { viewModel.contentViewState == .landing }

        #expect(updated == true)
        #expect(account.updatePublishedStateCalls >= 2)
    }

    @Test("account publisher triggers re-initialization when state is no longer initializing")
    func accountPublisherTriggersInitialization() async {
        let (viewModel, account, _, entry, _, _, _, _) = makeSUT()
        viewModel.contentViewState = .landing
        account.refreshAccountResult = .success(ContentViewModelTestFixtures.makeActiveAccount(id: "content-5", lastActiveTime: "t1"))
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
        account.refreshAccountResult = .success(ContentViewModelTestFixtures.makeActiveAccount(id: "content-7", lastActiveTime: "t1"))
        entry.allEntriesResult = .success(ContentViewModelTestFixtures.makeEntries(accountId: "content-7", count: 1))

        account.activeAccount = ContentViewModelTestFixtures.makeActiveAccount(id: "content-7", lastActiveTime: "t1")
        _ = await waitUntil { account.refreshAccountCalls == 1 }

        viewModel.contentViewState = .landing
        account.activeAccount = ContentViewModelTestFixtures.makeActiveAccount(id: "content-7", lastActiveTime: "t1")
        _ = await waitUntil(timeoutNanoseconds: 200_000_000) { true }

        #expect(account.refreshAccountCalls == 1)
    }

    @Test("account publisher re-initializes when same account has new lastActiveTime")
    func accountPublisherReinitializesOnNewLastActiveTime() async {
        let (viewModel, account, _, entry, _, _, _, _) = makeSUT()
        viewModel.contentViewState = .landing
        account.refreshAccountResult = .success(ContentViewModelTestFixtures.makeActiveAccount(id: "content-8", lastActiveTime: "t1"))
        entry.allEntriesResult = .success(ContentViewModelTestFixtures.makeEntries(accountId: "content-8", count: 1))

        account.activeAccount = ContentViewModelTestFixtures.makeActiveAccount(id: "content-8", lastActiveTime: "t1")
        _ = await waitUntil { account.refreshAccountCalls == 1 }

        viewModel.contentViewState = .landing
        account.activeAccount = ContentViewModelTestFixtures.makeActiveAccount(id: "content-8", lastActiveTime: "t2")
        let updated = await waitUntil { account.refreshAccountCalls == 2 }

        #expect(updated == true)
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
        let processed = await waitUntil(timeoutNanoseconds: 3_000_000_000) { accountFlag.checkAccountFlagCalls == 1 }

        #expect(processed == true)
        #expect(accountFlag.getAccountFlagCalls == 1)
        #expect(accountFlag.checkAccountFlagCalls == 1)
        #expect(accountFlag.lastCheckTrigger == "entry")
    }

    @Test("checkAccountFlagsAfterLogin processes account flag with login trigger when flag exists")
    func checkAccountFlagsAfterLoginProcessesFlag() async {
        let (viewModel, account, _, entry, _, _, _, accountFlag) = makeSUT()
        account.activeAccount = ContentViewModelTestFixtures.makeActiveAccount(id: "content-flag-1")
        account.refreshAccountResult = .success(ContentViewModelTestFixtures.makeActiveAccount(id: "content-flag-1"))
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
        account.refreshAccountResult = .success(ContentViewModelTestFixtures.makeActiveAccount(id: "content-flag-error-1"))
        entry.allEntriesResult = .success(ContentViewModelTestFixtures.makeEntries(accountId: "content-flag-error-1", count: 1))
        accountFlag.getAccountFlagResult = .failure(ContentViewModelTestError.accountFlagFailed)

        viewModel.performAppInitialization()
        let completed = await waitUntil { viewModel.contentViewState == .dashboard }

        #expect(completed == true)
        #expect(accountFlag.getAccountFlagCalls == 1)
        #expect(accountFlag.checkAccountFlagCalls == 0)
    }

    @Test("checkAccountFlags handles checkAccountFlag error gracefully")
    func checkAccountFlagsHandlesCheckAccountFlagError() async {
        let (viewModel, account, _, entry, _, _, _, accountFlag) = makeSUT()
        account.activeAccount = ContentViewModelTestFixtures.makeActiveAccount(id: "content-flag-error-2")
        account.refreshAccountResult = .success(ContentViewModelTestFixtures.makeActiveAccount(id: "content-flag-error-2"))
        entry.allEntriesResult = .success(ContentViewModelTestFixtures.makeEntries(accountId: "content-flag-error-2", count: 1))
        accountFlag.getAccountFlagResult = .success(ContentViewModelTestFixtures.makeAccountFlag(trigger: "login"))
        accountFlag.checkAccountFlagResult = .failure(ContentViewModelTestError.accountFlagFailed)

        viewModel.performAppInitialization()
        let completed = await waitUntil(timeoutNanoseconds: 3_000_000_000) {
            accountFlag.checkAccountFlagCalls == 1 && viewModel.contentViewState == .dashboard
        }

        #expect(completed == true)
        #expect(accountFlag.getAccountFlagCalls == 1)
        #expect(accountFlag.checkAccountFlagCalls == 1)
        #expect(accountFlag.lastCheckTrigger == "login")
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
) -> (
    viewModel: ContentViewModel,
    accountService: MockAccountService,
    loggerService: MockLoggerService,
    entryService: MockContentViewModelEntryService,
    feedService: MockContentViewModelFeedService,
    scaleService: MockContentViewModelScaleService,
    bluetoothService: MockContentViewModelBluetoothService,
    accountFlagService: MockContentViewModelAccountFlagService
) {
    TestDependencyContainer.reset()

    let account = accountService ?? MockAccountService()
    let logger = loggerService ?? MockLoggerService()
    let entry = entryService ?? MockContentViewModelEntryService()
    let feed = feedService ?? MockContentViewModelFeedService()
    let scale = scaleService ?? MockContentViewModelScaleService()
    let bluetooth = bluetoothService ?? MockContentViewModelBluetoothService()
    let accountFlag = accountFlagService ?? MockContentViewModelAccountFlagService()
    let keychain = MockKeychainService()

    TestDependencyContainer.registerBase(logger: logger, keychain: keychain, bluetooth: bluetooth)

    DependencyContainer.shared.register(account as AccountServiceProtocol)
    DependencyContainer.shared.register(entry as EntryServiceProtocol)
    DependencyContainer.shared.register(feed as FeedServiceProtocol)
    DependencyContainer.shared.register(scale as ScaleServiceProtocol)
    DependencyContainer.shared.register(bluetooth as BluetoothServiceProtocol)
    DependencyContainer.shared.register(accountFlag as AccountFlagServiceProtocol)
    DependencyContainer.shared.register(logger as LoggerServiceProtocol)

    let viewModel = ContentViewModel()
    return (viewModel, account, logger, entry, feed, scale, bluetooth, accountFlag)
}

@MainActor
private func waitUntil(
    timeoutNanoseconds: UInt64 = 2_000_000_000,
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
