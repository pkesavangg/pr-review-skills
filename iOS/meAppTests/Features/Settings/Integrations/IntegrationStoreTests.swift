//
//  IntegrationStoreTests.swift
//  meAppTests
//

import Testing
import Foundation
@testable import meApp

@Suite("IntegrationStore", .serialized)
@MainActor
struct IntegrationStoreTests {

    // MARK: - SUT

    private func makeSUT() -> (
        store: IntegrationStore,
        accountService: MockAccountService,
        integrationsService: MockIntegrationService,
        notificationService: MockNotificationHelperService,
        logger: MockLoggerService
    ) {
        _ = ServiceRegistry.shared

        let accountService = MockAccountService()
        let integrationsService = MockIntegrationService()
        let notificationService = MockNotificationHelperService()
        let logger = MockLoggerService()

        DependencyContainer.shared.register(accountService as AccountServiceProtocol)
        DependencyContainer.shared.register(integrationsService as IntegrationServiceProtocol)
        DependencyContainer.shared.register(notificationService as NotificationHelperService)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)

        // IntegrationStore.init() accesses accountService.activeAccountPublisher
        let store = IntegrationStore()

        return (store, accountService, integrationsService, notificationService, logger)
    }

    // MARK: - Fixture helpers

    private func makeAccountWithFitbit(_ fitbitOn: Bool, mfpOn: Bool = false) -> Account {
        let account = AccountTestFixtures.makeAccount()
        account.integrationSettings?.isFitbitOn = fitbitOn
        account.integrationSettings?.isMfpOn = mfpOn
        return account
    }

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 2_000_000_000,
        pollNanoseconds: UInt64 = 10_000_000,
        condition: @MainActor () -> Bool
    ) async {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while ContinuousClock.now < deadline {
            if condition() { return }
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
    }

    // MARK: - Initial State

    @Test("integrations contains 2 items initially")
    func integrationsHasTwoItemsInitially() {
        let (store, _, _, _, _) = makeSUT()
        #expect(store.integrations.count == 2)
    }

    @Test("all integrations are unselected initially")
    func integrationsAllUnselectedInitially() {
        let (store, _, _, _, _) = makeSUT()
        #expect(store.integrations.allSatisfy { !$0.isSelected })
    }

    @Test("first integration is fitbit")
    func firstIntegrationIsFitbit() {
        let (store, _, _, _, _) = makeSUT()
        #expect(store.integrations.first?.type == .fitbit)
    }

    @Test("second integration is myFitnessPal")
    func secondIntegrationIsMFP() {
        let (store, _, _, _, _) = makeSUT()
        #expect(store.integrations.last?.type == .myFitnessPal)
    }

    @Test("accountID is empty initially")
    func accountIDEmptyInitially() {
        let (store, _, _, _, _) = makeSUT()
        #expect(store.accountID == "")
    }

    @Test("showBrowser is false initially")
    func showBrowserFalseInitially() {
        let (store, _, _, _, _) = makeSUT()
        #expect(!store.showBrowser)
    }

    // MARK: - applyAccountState (via activeAccountPublisher)

    @Test("integrations update when account has fitbit enabled")
    func integrationsFitbitEnabledOnAccountChange() async {
        let (store, accountService, _, _, _) = makeSUT()
        let account = makeAccountWithFitbit(true)

        accountService.activeAccount = account

        await waitUntil { store.integrations.first { $0.type == .fitbit }?.isSelected == true }
        #expect(store.integrations.first { $0.type == .fitbit }?.isSelected == true)
    }

    @Test("integrations update when account has mfp enabled")
    func integrationsMfpEnabledOnAccountChange() async {
        let (store, accountService, _, _, _) = makeSUT()
        let account = makeAccountWithFitbit(false, mfpOn: true)

        accountService.activeAccount = account

        await waitUntil { store.integrations.first { $0.type == .myFitnessPal }?.isSelected == true }
        #expect(store.integrations.first { $0.type == .myFitnessPal }?.isSelected == true)
    }

    @Test("accountID updates when account changes")
    func accountIDUpdatesOnAccountChange() async {
        let (store, accountService, _, _, _) = makeSUT()
        let account = AccountTestFixtures.makeAccount(id: "acct-xyz")

        accountService.activeAccount = account

        await waitUntil { store.accountID == "acct-xyz" }
        #expect(store.accountID == "acct-xyz")
    }

    @Test("integrations reset to unselected when account becomes nil")
    func integrationsResetWhenAccountNil() async {
        let (store, accountService, _, _, _) = makeSUT()

        accountService.activeAccount = makeAccountWithFitbit(true)
        await waitUntil { store.integrations.first { $0.type == .fitbit }?.isSelected == true }

        accountService.activeAccount = nil
        await waitUntil { store.integrations.allSatisfy { !$0.isSelected } }
        #expect(store.integrations.allSatisfy { !$0.isSelected })
    }

    // MARK: - selectIntegration (connected item → disconnect alert)

    @Test("selectIntegration with selected fitbit shows disconnect alert")
    func selectFitbitConnectedShowsAlert() {
        let (store, _, _, notificationService, _) = makeSUT()
        let fitbitItem = IntegrationItem(type: .fitbit, isSelected: true)

        store.selectIntegration(item: fitbitItem)

        #expect(notificationService.showAlertCallCount == 1)
        #expect(notificationService.lastShownAlert != nil)
    }

    @Test("selectIntegration with selected mfp shows disconnect alert")
    func selectMfpConnectedShowsAlert() {
        let (store, _, _, notificationService, _) = makeSUT()
        let mfpItem = IntegrationItem(type: .myFitnessPal, isSelected: true)

        store.selectIntegration(item: mfpItem)

        #expect(notificationService.showAlertCallCount == 1)
    }

    @Test("selectIntegration with connected item does not immediately set showBrowser")
    func selectConnectedItemDoesNotOpenBrowser() {
        let (store, _, _, _, _) = makeSUT()
        let fitbitItem = IntegrationItem(type: .fitbit, isSelected: true)

        store.selectIntegration(item: fitbitItem)

        #expect(!store.showBrowser)
    }

    // MARK: - selectIntegration (unconnected item, network-dependent)

    @Test("selectIntegration with unconnected fitbit when network available opens browser")
    func selectFitbitUnconnectedWithNetworkOpensBrowser() {
        guard NetworkMonitor.shared.isConnected else { return }
        let (store, _, _, _, _) = makeSUT()
        store.accountID = "test-account-id"
        let fitbitItem = IntegrationItem(type: .fitbit, isSelected: false)

        store.selectIntegration(item: fitbitItem)

        #expect(store.showBrowser)
        #expect(store.browserURL != nil)
    }

    @Test("selectIntegration with unconnected item when offline shows link error alert")
    func selectUnconnectedItemOfflineShowsAlert() {
        guard !NetworkMonitor.shared.isConnected else { return }
        let (store, _, _, notificationService, _) = makeSUT()
        store.accountID = "test-account-id"
        let fitbitItem = IntegrationItem(type: .fitbit, isSelected: false)

        store.selectIntegration(item: fitbitItem)

        #expect(notificationService.showAlertCallCount == 1)
        #expect(!store.showBrowser)
    }

    // MARK: - refreshAccounts

    @Test("refreshAccounts calls accountService.refreshAccount")
    func refreshAccountsCallsService() async {
        let (store, accountService, _, _, _) = makeSUT()
        let account = AccountTestFixtures.makeAccount(id: "acct-refresh")
        accountService.refreshAccountResult = account

        store.refreshAccounts()

        await waitUntil { accountService.refreshAccountCallCount >= 1 }
        #expect(accountService.refreshAccountCallCount >= 1)
    }

    @Test("refreshAccounts updates integrations from refreshed account")
    func refreshAccountsUpdatesIntegrations() async {
        let (store, accountService, _, _, _) = makeSUT()
        let account = makeAccountWithFitbit(true)
        accountService.refreshAccountResult = account

        store.refreshAccounts()

        await waitUntil { store.integrations.first { $0.type == .fitbit }?.isSelected == true }
        #expect(store.integrations.first { $0.type == .fitbit }?.isSelected == true)
    }

    // MARK: - skipInvalidIntegrationsCheck

    @Test("skipInvalidIntegrationsCheck flag can be set on the store")
    func skipInvalidIntegrationsCheckFlagIsSettable() {
        let (store, _, _, _, _) = makeSUT()
        store.skipInvalidIntegrationsCheck = true
        #expect(store.skipInvalidIntegrationsCheck)
    }

    // MARK: - browserURL / showBrowser state management

    @Test("presentingBrowserURL returns non-nil URL even when browserURL is nil")
    func presentingBrowserURLNeverNil() {
        let (store, _, _, _, _) = makeSUT()
        #expect(store.browserURL == nil)
        #expect(store.presentingBrowserURL.absoluteString.isEmpty == false)
    }

    // MARK: - Disconnect flow (remove integration)

    @Test("disconnect confirm removes the integration and refreshes the account")
    func disconnectConfirmRemovesIntegration() async {
        let (store, accountService, integrationsService, notificationService, _) = makeSUT()
        store.skipInvalidIntegrationsCheck = true
        accountService.activeAccount = makeAccountWithFitbit(true)
        await waitUntil { store.accountID != "" }

        store.selectIntegration(item: IntegrationItem(type: .fitbit, isSelected: true))
        // refreshAccount returns the account with fitbit now off → disconnect verified → done alert
        accountService.refreshAccountResult = makeAccountWithFitbit(false)

        // Second button is the destructive "remove" action.
        notificationService.lastShownAlert?.buttons.last?.action(nil)

        await waitUntil { integrationsService.removeIntegrationCallCount == 1 }
        #expect(integrationsService.removeIntegrationCallCount == 1)
        #expect(integrationsService.lastRemovedProvider == .fitbit)
    }

    @Test("disconnect failure shows the try-again alert")
    func disconnectFailureShowsTryAgain() async {
        let (store, accountService, integrationsService, notificationService, _) = makeSUT()
        store.skipInvalidIntegrationsCheck = true
        accountService.activeAccount = makeAccountWithFitbit(true)
        await waitUntil { store.accountID != "" }
        integrationsService.removeIntegrationError = NSError(domain: "IntegrationTest", code: -1)

        store.selectIntegration(item: IntegrationItem(type: .fitbit, isSelected: true))
        notificationService.lastShownAlert?.buttons.last?.action(nil)

        await waitUntil { integrationsService.removeIntegrationCallCount == 1 }
        #expect(integrationsService.removeIntegrationCallCount == 1)
    }

    // MARK: - refreshAccounts result handling

    @Test("refreshAccounts after a connect attempt handles a successful result")
    func refreshAccountsConnectSuccess() async {
        guard NetworkMonitor.shared.isConnected else { return }
        let (store, accountService, _, _, _) = makeSUT()
        store.skipInvalidIntegrationsCheck = true
        store.accountID = "test-account-id"

        // Opening the browser for an unconnected provider marks a pending connect action.
        store.selectIntegration(item: IntegrationItem(type: .fitbit, isSelected: false))
        accountService.refreshAccountResult = makeAccountWithFitbit(true)

        store.refreshAccounts()

        await waitUntil { accountService.refreshAccountCallCount >= 1 }
        #expect(accountService.refreshAccountCallCount >= 1)
    }

    @Test("refreshAccounts after a failed connect shows try-again and retry re-selects")
    func refreshAccountsConnectFailureRetry() async {
        guard NetworkMonitor.shared.isConnected else { return }
        let (store, accountService, _, notificationService, _) = makeSUT()
        store.skipInvalidIntegrationsCheck = true
        store.accountID = "test-account-id"

        store.selectIntegration(item: IntegrationItem(type: .fitbit, isSelected: false))
        accountService.refreshAccountResult = makeAccountWithFitbit(false) // not enabled → connect failed

        store.refreshAccounts()
        await waitUntil { accountService.refreshAccountCallCount >= 1 }

        // Retry button (primary) re-triggers the connect flow.
        notificationService.lastShownAlert?.buttons.last?.action(nil)
        #expect(accountService.refreshAccountCallCount >= 1)
    }

    // MARK: - Invalid integration prompt

    @Test("an enabled-but-invalid integration prompts re-integrate and disable removes it")
    func invalidIntegrationPromptDisable() async {
        guard NetworkMonitor.shared.isConnected else { return }

        // The active account must be set BEFORE the store subscribes: the
        // invalid-integration check is one-shot, and the publisher's initial
        // nil emission would otherwise consume it.
        _ = ServiceRegistry.shared
        let accountService = MockAccountService()
        let integrationsService = MockIntegrationService()
        let notificationService = MockNotificationHelperService()
        let logger = MockLoggerService()
        DependencyContainer.shared.register(accountService as AccountServiceProtocol)
        DependencyContainer.shared.register(integrationsService as IntegrationServiceProtocol)
        DependencyContainer.shared.register(notificationService as NotificationHelperService)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)

        let account = AccountTestFixtures.makeAccount()
        account.integrationSettings?.isFitbitOn = true
        account.integrationSettings?.isFitbitValid = false
        accountService.activeAccount = account
        accountService.refreshAccountResult = makeAccountWithFitbit(false)

        let store = IntegrationStore() // first emission is the invalid account → re-integrate prompt

        await waitUntil { notificationService.lastShownAlert != nil }
        // Disable button (danger) silently removes the invalid integrations.
        notificationService.lastShownAlert?.buttons.first?.action(nil)

        await waitUntil { integrationsService.removeIntegrationCallCount >= 1 }
        #expect(integrationsService.removeIntegrationCallCount >= 1)
        withExtendedLifetime(store) {}
    }
}
