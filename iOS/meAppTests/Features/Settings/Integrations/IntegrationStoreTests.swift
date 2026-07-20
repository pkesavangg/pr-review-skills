import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct IntegrationStoreTests {
    @Test("initialization maps account integration state into list")
    func initializationMapsAccountState() {
        let account = MockAccountService()
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(
            id: "acc-1",
            fitbitOn: true,
            mfpOn: false
        )
        let (store, _, _, _, _) = makeSUT(accountService: account)

        #expect(store.accountID == "acc-1")
        #expect(store.integrations.first { $0.type == .fitbit }?.isSelected == true)
        #expect(store.integrations.first { $0.type == .myFitnessPal }?.isSelected == false)
    }

    @Test("account publisher updates list mapping when account changes")
    func accountPublisherUpdatesListMapping() async {
        let account = MockAccountService()
        let (store, _, _, _, _) = makeSUT(accountService: account)

        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(
            id: "acc-2",
            fitbitOn: false,
            mfpOn: true
        )
        let updated = await waitUntil {
            store.accountID == "acc-2"
                && store.integrations.first { $0.type == .fitbit }?.isSelected == false
                && store.integrations.first { $0.type == .myFitnessPal }?.isSelected == true
        }

        #expect(updated == true)
    }

    @Test("selectIntegration connect path presents browser and sets oauth URL")
    func selectIntegrationConnectPathPresentsBrowser() {
        let account = MockAccountService()
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-3")
        let (store, _, _, _, _) = makeSUT(accountService: account, monitor: MockNetworkMonitor(isConnected: true))

        store.selectIntegration(item: IntegrationItem(type: .fitbit, isSelected: false))

        #expect(store.showBrowser == true)
        #expect(store.browserURL?.absoluteString.contains("fitbit") == true)
        #expect(store.browserURL?.absoluteString.contains("acc-3") == true)
    }

    @Test("presentingBrowserURL uses fallback when browserURL is nil")
    func presentingBrowserURLFallback() {
        let (store, _, _, _, _) = makeSUT()
        store.browserURL = nil

        #expect(store.presentingBrowserURL == AppConstants.LegalURLs.greaterGoodsWebsite)
    }

    @Test("selectIntegration connect with no network shows link-open alert and does not open browser")
    func selectIntegrationNoNetworkShowsAlert() {
        let account = MockAccountService()
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-4")
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _) = makeSUT(
            accountService: account,
            notificationService: notification,
            monitor: MockNetworkMonitor(isConnected: false)
        )

        store.selectIntegration(item: IntegrationItem(type: .fitbit, isSelected: false))

        #expect(store.showBrowser == false)
        #expect(notification.showAlertCalls == 1)
        #expect(notification.alertData?.buttons.count == 2)
    }

    @Test("link-open alert copy button executes without changing browser state")
    func linkOpenAlertCopyButtonAction() {
        let account = MockAccountService()
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-4b")
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _) = makeSUT(
            accountService: account,
            notificationService: notification,
            monitor: MockNetworkMonitor(isConnected: false)
        )

        store.selectIntegration(item: IntegrationItem(type: .fitbit, isSelected: false))
        notification.alertData?.buttons.last?.action(nil)

        #expect(store.showBrowser == false)
        #expect(notification.showAlertCalls == 1)
    }

    @Test("selectIntegration selected item shows remove confirmation alert")
    func selectIntegrationSelectedShowsRemoveConfirmation() {
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _) = makeSUT(notificationService: notification)

        store.selectIntegration(item: IntegrationItem(type: .fitbit, isSelected: true))

        #expect(notification.showAlertCalls == 1)
        #expect(notification.alertData?.buttons.count == 2)
        #expect(notification.alertData?.buttons.first?.type == .secondary)
        #expect(notification.alertData?.buttons.last?.type == .danger)
    }

    @Test("disconnect confirmation success removes integration, refreshes account, and shows completion alert")
    func disconnectConfirmationSuccess() async {
        let account = MockAccountService()
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-5", fitbitOn: true)
        account.refreshAccountResult = .success(())
        // After removal + refresh, the server reports Fitbit disabled.
        account.refreshAccountAppliesAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-5", fitbitOn: false)
        let integrationService = MockIntegrationStoreService()
        let notification = MockNotificationHelperService()

        let (store, _, _, _, _) = makeSUT(
            accountService: account,
            integrationService: integrationService,
            notificationService: notification
        )

        store.selectIntegration(item: IntegrationItem(type: .fitbit, isSelected: true))
        notification.alertData?.buttons.last?.action(nil)
        // Wait for the whole disconnect flow to settle — not just dismissLoader. The
        // publisher-driven integrations refresh and the completion alert are the states
        // this test asserts, so gate on them too to avoid a flaky read mid-flow.
        let completed = await waitUntil {
            integrationService.removeIntegrationCalls == [.fitbit]
                && account.refreshAccountCalls == 1
                && notification.dismissLoaderCalls == 1
                && store.integrations.first { $0.type == .fitbit }?.isSelected == false
                && notification.alertData?.buttons.count == 1
        }

        #expect(completed == true)
        #expect(store.integrations.first { $0.type == .fitbit }?.isSelected == false)
        #expect(notification.alertData?.buttons.count == 1)
        #expect(notification.alertData?.buttons.first?.type == .primary)
    }

    @Test("disconnect failure no-internet does not show retry alert")
    func disconnectFailureNoInternet() async {
        let account = MockAccountService()
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-6", fitbitOn: true)
        let integrationService = MockIntegrationStoreService()
        integrationService.removeIntegrationError = HTTPError.noInternet
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _) = makeSUT(
            accountService: account,
            integrationService: integrationService,
            notificationService: notification
        )

        store.selectIntegration(item: IntegrationItem(type: .fitbit, isSelected: true))
        notification.alertData?.buttons.last?.action(nil)
        _ = await waitUntil { notification.dismissLoaderCalls == 1 }

        #expect(notification.showAlertCalls == 1)
    }

    @Test("disconnect failure non-network shows retry alert")
    func disconnectFailureShowsRetry() async {
        let account = MockAccountService()
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-7", fitbitOn: true)
        let integrationService = MockIntegrationStoreService()
        integrationService.removeIntegrationError = IntegrationStoreTestError.removeFailed
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _) = makeSUT(
            accountService: account,
            integrationService: integrationService,
            notificationService: notification
        )

        store.selectIntegration(item: IntegrationItem(type: .fitbit, isSelected: true))
        notification.alertData?.buttons.last?.action(nil)
        let updated = await waitUntil { notification.showAlertCalls >= 2 }

        #expect(updated == true)
        #expect(notification.alertData?.buttons.count == 2)
        #expect(notification.alertData?.buttons.last?.type == .primary)
    }

    @Test("refreshAccounts handles pending connect success with completion alert")
    func refreshAccountsPendingConnectSuccess() async {
        let account = MockAccountService()
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-8", fitbitOn: false)
        account.refreshAccountResult = .success(())
        // After the OAuth flow completes, refreshing the account reports Fitbit enabled.
        account.refreshAccountAppliesAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-8", fitbitOn: true)
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _) = makeSUT(accountService: account, notificationService: notification)

        store.selectIntegration(item: IntegrationItem(type: .fitbit, isSelected: false))
        store.refreshAccounts()
        let refreshed = await waitUntil {
            account.refreshAccountCalls == 1
                && store.integrations.first { $0.type == .fitbit }?.isSelected == true
        }

        #expect(refreshed == true)
        #expect(store.integrations.first { $0.type == .fitbit }?.isSelected == true)
        #expect(notification.showAlertCalls == 1)
        #expect(notification.alertData?.buttons.count == 1)
    }

    @Test("refreshAccounts handles pending connect failure with retry alert")
    func refreshAccountsPendingConnectFailure() async {
        let account = MockAccountService()
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-9", fitbitOn: false)
        account.refreshAccountResult = .success(())
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _) = makeSUT(accountService: account, notificationService: notification)

        store.selectIntegration(item: IntegrationItem(type: .fitbit, isSelected: false))
        store.refreshAccounts()
        let refreshed = await waitUntil { account.refreshAccountCalls == 1 }

        #expect(refreshed == true)
        #expect(notification.showAlertCalls == 1)
        #expect(notification.alertData?.buttons.count == 2)
    }

    @Test("refreshAccounts handles pending disconnect failure with retry alert")
    func refreshAccountsPendingDisconnectFailure() async {
        let account = MockAccountService()
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-9b", fitbitOn: true)
        account.refreshAccountResult = .success(())
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _) = makeSUT(accountService: account, notificationService: notification)

        store.selectIntegration(item: IntegrationItem(type: .fitbit, isSelected: true))
        store.refreshAccounts()
        let refreshed = await waitUntil { account.refreshAccountCalls == 1 }

        #expect(refreshed == true)
        #expect(notification.showAlertCalls == 2)
        #expect(notification.alertData?.buttons.count == 2)
    }

    @Test("refreshAccounts refresh failure does not present additional alert")
    func refreshAccountsFailureNoExtraAlert() async {
        let account = MockAccountService()
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-10", fitbitOn: false)
        account.refreshAccountResult = .failure(IntegrationStoreTestError.refreshFailed)
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _) = makeSUT(accountService: account, notificationService: notification)

        store.selectIntegration(item: IntegrationItem(type: .fitbit, isSelected: false))
        store.refreshAccounts()
        let refreshed = await waitUntil { account.refreshAccountCalls == 1 }

        #expect(refreshed == true)
        #expect(notification.showAlertCalls == 0)
    }

    @Test("retry button on connect failure re-triggers connect flow")
    func retryConnectFailureRetriggersSelectIntegration() async {
        let account = MockAccountService()
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-10b", fitbitOn: false)
        account.refreshAccountResult = .success(())
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _) = makeSUT(accountService: account, notificationService: notification)

        store.selectIntegration(item: IntegrationItem(type: .fitbit, isSelected: false))
        store.refreshAccounts()
        _ = await waitUntil { notification.alertData?.buttons.count == 2 }

        store.showBrowser = false
        store.browserURL = nil
        notification.alertData?.buttons.last?.action(nil)

        #expect(store.showBrowser == true)
        #expect(store.browserURL?.absoluteString.contains("fitbit") == true)
    }

    @Test("retry button on disconnect failure re-attempts remove integration")
    func retryDisconnectFailureRetriesRemoval() async {
        let account = MockAccountService()
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-10c", fitbitOn: true)
        let integrationService = MockIntegrationStoreService()
        integrationService.removeIntegrationError = IntegrationStoreTestError.removeFailed
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _) = makeSUT(
            accountService: account,
            integrationService: integrationService,
            notificationService: notification
        )

        store.selectIntegration(item: IntegrationItem(type: .fitbit, isSelected: true))
        notification.alertData?.buttons.last?.action(nil)
        _ = await waitUntil { notification.alertData?.buttons.count == 2 }
        let firstAttempts = integrationService.removeIntegrationCalls.count

        notification.alertData?.buttons.last?.action(nil)
        let retried = await waitUntil {
            integrationService.removeIntegrationCalls.count > firstAttempts
        }

        #expect(retried == true)
    }

    @Test("invalid integrations prompt disable action removes each invalid provider")
    func invalidIntegrationsDisableAllAction() async {
        let account = MockAccountService()
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(
            id: "acc-11",
            fitbitOn: true,
            fitbitValid: false,
            mfpOn: true,
            mfpValid: false
        )
        account.refreshAccountResult = .success(())
        let integrationService = MockIntegrationStoreService()
        let notification = MockNotificationHelperService()
        let (_, _, _, _, _) = makeSUT(
            accountService: account,
            integrationService: integrationService,
            notificationService: notification,
            monitor: MockNetworkMonitor(isConnected: true)
        )

        #expect(notification.showAlertCalls == 1)
        notification.alertData?.buttons.first?.action(nil)
        let completed = await waitUntil {
            integrationService.removeIntegrationCalls.count == 2
                && account.refreshAccountCalls == 2
                && notification.showAlertCalls >= 2
        }

        #expect(completed == true)
        #expect(integrationService.removeIntegrationCalls == [.fitbit, .myFitnessPal])
    }

    @Test("invalid integration check runs once per store lifecycle")
    func invalidIntegrationCheckRunsOnce() async {
        let account = MockAccountService()
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-12", fitbitOn: true, fitbitValid: false)
        let notification = MockNotificationHelperService()
        let (_, _, _, _, _) = makeSUT(
            accountService: account,
            notificationService: notification,
            monitor: MockNetworkMonitor(isConnected: true)
        )

        #expect(notification.showAlertCalls == 1)
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-12", fitbitOn: true, fitbitValid: false)
        _ = await waitUntil { account.activeAccount != nil }

        #expect(notification.showAlertCalls == 1)
    }

    @Test("invalid integration check is skipped when offline")
    func invalidIntegrationCheckSkippedOffline() {
        let account = MockAccountService()
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-13", fitbitOn: true, fitbitValid: false)
        let notification = MockNotificationHelperService()

        _ = makeSUT(
            accountService: account,
            notificationService: notification,
            monitor: MockNetworkMonitor(isConnected: false)
        )

        #expect(notification.showAlertCalls == 0)
    }

    @Test("skipInvalidIntegrationsCheck prevents invalid integration check")
    func skipInvalidIntegrationsCheckPreventsCheck() async {
        let account = MockAccountService()
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _) = makeSUT(
            accountService: account,
            notificationService: notification,
            monitor: MockNetworkMonitor(isConnected: true)
        )

        // Set flag before account is set to prevent check
        store.skipInvalidIntegrationsCheck = true
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-14", fitbitOn: true, fitbitValid: false)
        _ = await waitUntil { account.activeAccount != nil }

        #expect(notification.showAlertCalls == 0)
    }

    @Test("selectIntegration with unsupported type returns early without browser or alert")
    func selectIntegrationUnsupportedTypeReturnsEarly() {
        let account = MockAccountService()
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-15")
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _) = makeSUT(
            accountService: account,
            notificationService: notification,
            monitor: MockNetworkMonitor(isConnected: true)
        )

        store.selectIntegration(item: IntegrationItem(type: .appleHealth, isSelected: false))

        #expect(store.showBrowser == false)
        #expect(store.browserURL == nil)
        #expect(notification.showAlertCalls == 0)
    }

    @Test("performRemoveIntegration with unsupported type returns early without service call")
    func performRemoveIntegrationUnsupportedTypeReturnsEarly() async {
        let account = MockAccountService()
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-16", fitbitOn: true)
        let integrationService = MockIntegrationStoreService()
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _) = makeSUT(
            accountService: account,
            integrationService: integrationService,
            notificationService: notification
        )

        store.selectIntegration(item: IntegrationItem(type: .appleHealth, isSelected: true))
        notification.alertData?.buttons.last?.action(nil)
        // Wait a bit to ensure async operations complete
        try? await Task.sleep(nanoseconds: 100_000_000) // 0.1 seconds

        #expect(integrationService.removeIntegrationCalls.isEmpty == true)
        #expect(account.refreshAccountCalls == 0)
        #expect(notification.showLoaderCalls == 1)
        // Note: Loader dismissal behavior depends on implementation - if mapToIntegrationType returns nil,
        // the function returns early and loader may not be dismissed
    }

    @Test("paired-device changes re-render the device-driven provider sections")
    func availableItemsChangeUpdatesProviderSections() async {
        let account = MockAccountService()
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-dev", fitbitOn: true, mfpOn: true)
        let (store, _, _, _, _) = makeSUT(accountService: account)

        // Default available items contain .myWeight → weight-scale providers are listed.
        #expect(store.integrations.contains { $0.type == .fitbit })
        #expect(store.integrations.contains { $0.type == .myFitnessPal })

        // The store observes the mock registered under the protocol key; the concrete
        // "ProductTypeStore" key is later overwritten by the real shared store in DI setup.
        guard let productTypeStore = DependencyContainer.shared.dependencies["ProductTypeStoreProtocol"] as? MockProductTypeStore else {
            Issue.record("Expected the store's product-type store to be a MockProductTypeStore")
            return
        }

        // No weight device available → the weight-scale provider section disappears.
        // The availableItems publisher fires during `willSet`, so settle the new value first,
        // then let an account emission recompute the list (applyAccountState reads availableItems fresh).
        productTypeStore.availableItems = [.myBloodPressure]
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-dev-bp", fitbitOn: true, mfpOn: true)
        let hidden = await waitUntil { store.integrations.isEmpty }
        #expect(hidden == true)

        // A weight scale is available again → the provider section reappears.
        productTypeStore.availableItems = [.myWeight]
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-dev-wt", fitbitOn: true, mfpOn: true)
        let shown = await waitUntil {
            store.integrations.contains { $0.type == .fitbit }
                && store.integrations.contains { $0.type == .myFitnessPal }
        }
        #expect(shown == true)
    }

    // MARK: - submitIntegrationRequest

    @Test("submitIntegrationRequest success: shows loader then success alert")
    func submitIntegrationRequestSuccess() async {
        let integration = MockIntegrationStoreService()
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _) = makeSUT(
            integrationService: integration,
            notificationService: notification
        )

        await store.submitIntegrationRequest(text: "Garmin sync")

        #expect(integration.requestNewIntegrationCalls == ["Garmin sync"])
        #expect(notification.showLoaderCalls == 1)
        #expect(notification.dismissLoaderCalls == 1)
        // Success alert shown with a single dismiss button
        #expect(notification.showAlertCalls == 1)
        #expect(notification.alertData?.title == IntegrationsStrings.requestIntegrationSuccessTitle)
        #expect(notification.alertData?.buttons.count == 1)
    }

    @Test("submitIntegrationRequest failure: shows loader then error alert")
    func submitIntegrationRequestFailure() async {
        let integration = MockIntegrationStoreService()
        integration.requestNewIntegrationError = IntegrationStoreTestError.removeFailed
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _) = makeSUT(
            integrationService: integration,
            notificationService: notification
        )

        await store.submitIntegrationRequest(text: "Garmin sync")

        #expect(integration.requestNewIntegrationCalls == ["Garmin sync"])
        #expect(notification.showLoaderCalls == 1)
        #expect(notification.dismissLoaderCalls == 1)
        // Error alert shown with a single dismiss button
        #expect(notification.showAlertCalls == 1)
        #expect(notification.alertData?.title == IntegrationsStrings.requestIntegrationErrorTitle)
        #expect(notification.alertData?.buttons.count == 1)
    }

    @Test("submitIntegrationRequest no-internet: dismisses loader without stacking error alert")
    func submitIntegrationRequestNoInternet() async {
        let integration = MockIntegrationStoreService()
        integration.requestNewIntegrationError = HTTPError.noInternet
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _) = makeSUT(
            integrationService: integration,
            notificationService: notification
        )

        await store.submitIntegrationRequest(text: "Garmin sync")

        #expect(integration.requestNewIntegrationCalls == ["Garmin sync"])
        #expect(notification.showLoaderCalls == 1)
        #expect(notification.dismissLoaderCalls == 1)
        // The HTTP layer already surfaces the "no network" toast; no redundant alert.
        #expect(notification.showAlertCalls == 0)
    }

    @Test("showRequestIntegrationModal presents a backdrop-dismissible modal")
    func showRequestIntegrationModalPresentsModal() {
        let notification = MockNotificationHelperService()
        let (store, _, _, _, _) = makeSUT(notificationService: notification)

        store.showRequestIntegrationModal()

        #expect(notification.showModalCalls == 1)
    }

    @Test("account publisher handles nil account correctly")
    func accountPublisherHandlesNilAccount() async {
        let account = MockAccountService()
        account.activeAccount = IntegrationStoreTestFixtures.makeAccount(id: "acc-17", fitbitOn: true, mfpOn: true)
        let (store, _, _, _, _) = makeSUT(accountService: account)

        account.activeAccount = nil
        let updated = await waitUntil {
            store.accountID.isEmpty
                && store.integrations.first { $0.type == .fitbit }?.isSelected == false
                && store.integrations.first { $0.type == .myFitnessPal }?.isSelected == false
        }

        #expect(updated == true)
    }
}

@MainActor
private func makeSUT(
    accountService: MockAccountService? = nil,
    integrationService: MockIntegrationStoreService? = nil,
    notificationService: MockNotificationHelperService? = nil,
    loggerService: MockLoggerService? = nil,
    monitor: MockNetworkMonitor? = nil
) -> ( // swiftlint:disable:this large_tuple
    store: IntegrationStore,
    accountService: MockAccountService,
    integrationService: MockIntegrationStoreService,
    notificationService: MockNotificationHelperService,
    loggerService: MockLoggerService
) {
    TestDependencyContainer.reset()

    let logger = loggerService ?? MockLoggerService()
    let keychain = MockKeychainService()
    let bluetooth = MockBluetoothService()
    TestDependencyContainer.registerBase(logger: logger, keychain: keychain, bluetooth: bluetooth)

    let account = accountService ?? MockAccountService()
    let integration = integrationService ?? MockIntegrationStoreService()
    let notification = notificationService ?? MockNotificationHelperService()

    DependencyContainer.shared.register(logger as LoggerServiceProtocol)
    DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
    DependencyContainer.shared.register(account as AccountServiceProtocol)
    DependencyContainer.shared.register(integration as IntegrationServiceProtocol)

    let store = IntegrationStore(networkMonitor: monitor ?? MockNetworkMonitor(isConnected: true))
    return (store, account, integration, notification, logger)
}

@MainActor
private func waitUntil(
    timeoutIterations: Int = 300,
    condition: @MainActor () -> Bool
) async -> Bool {
    for _ in 0..<timeoutIterations {
        if condition() { return true }
        await Task.yield()
    }
    return false
}
