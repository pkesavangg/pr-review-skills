import Foundation
@testable import meApp

enum SettingsStoreTestFixtures {
    @MainActor
    static func makeSUT(
        notification: TestNotificationHelperService? = nil,
        accountService: MockAccountService? = nil,
        entryService: MockEntryService? = nil,
        feedService: MockFeedService? = nil,
        bluetoothService: MockBluetoothService? = nil,
        integrationService: MockIntegrationService? = nil,
        logger: MockLoggerService? = nil,
        seedDefaultAccount: Bool = true
    ) -> (SettingsStore, TestNotificationHelperService, MockAccountService, MockEntryService, MockFeedService) { // swiftlint:disable:this large_tuple
        TestDependencyContainer.reset()

        let notification = notification ?? TestNotificationHelperService()
        let accountService = accountService ?? MockAccountService()
        let entryService = entryService ?? MockEntryService()
        let feedService = feedService ?? MockFeedService()
        let bluetoothService = bluetoothService ?? MockBluetoothService()
        let integrationService = integrationService ?? MockIntegrationService()
        let logger = logger ?? MockLoggerService()

        if seedDefaultAccount && accountService.activeAccount == nil {
            let account = makeAccount()
            accountService.seedAccounts([account], active: account)
        }

        DependencyContainer.shared.register(notification as NotificationHelperService)
        DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
        DependencyContainer.shared.register(accountService as AccountServiceProtocol)
        DependencyContainer.shared.register(entryService as EntryServiceProtocol)
        DependencyContainer.shared.register(feedService as FeedServiceProtocol)
        DependencyContainer.shared.register(GoalAlertService.shared)
        DependencyContainer.shared.register(bluetoothService as BluetoothServiceProtocol)
        DependencyContainer.shared.register(integrationService as IntegrationServiceProtocol)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)

        Theme.shared.appearanceMode = .system

        let store = SettingsStore()
        store.accountService = accountService
        store.notificationService = notification
        store.entryService = entryService
        store.logger = logger
        store.feedService = feedService
        store.goalAlertService = GoalAlertService.shared
        store.bluetoothService = bluetoothService
        store.integrationService = integrationService
        store.useModalPicker = false

        return (store, notification, accountService, entryService, feedService)
    }

    @MainActor
    static func makeAccount(
        id: String = "acct-1",
        email: String = "lakshmi@example.com",
        firstName: String = "Lakshmi",
        unit: WeightUnit = .kg
    ) -> AccountSnapshot {
        AccountTestFixtures.makeAccountSnapshot(
            id: id,
            email: email,
            firstName: firstName,
            lastName: "Priya",
            gender: .female,
            dob: "1992-03-04",
            zipcode: "560001",
            isActiveAccount: true,
            weightUnit: unit,
            weightHeight: "681",
            activityLevel: .athlete,
            isStreakOn: true,
            isWeightlessOn: true,
            weightlessWeight: 1550,
            shouldSendEntryNotifications: true,
            shouldSendWeightInEntryNotifications: true
        )
    }

    @MainActor
    static func waitUntil(
        timeoutNanoseconds: UInt64 = 1_000_000_000,
        pollNanoseconds: UInt64 = 20_000_000,
        condition: @escaping @MainActor () -> Bool
    ) async {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while !condition() && ContinuousClock.now < deadline {
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
    }
}
