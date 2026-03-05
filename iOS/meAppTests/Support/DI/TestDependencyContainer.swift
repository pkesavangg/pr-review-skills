import Foundation
@testable import meApp

@MainActor
enum TestDependencyContainer {
    typealias DashboardConcreteDependencies = (
        account: AccountService,
        logger: LoggerService,
        scale: ScaleService,
        entry: EntryService
    )

    static func reset() {
        DependencyContainer.shared.dependencies.removeAll()
        // Keep core injected protocols available even before per-suite overrides are registered.
        registerBase(
            logger: MockLoggerService(),
            keychain: MockKeychainService(),
            bluetooth: MockBluetoothService()
        )
        let notification = MockNotificationHelperService()
        DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
        DependencyContainer.shared.register(MockEntryService() as EntryServiceProtocol)
        DependencyContainer.shared.register(MockContentViewModelFeedService() as FeedServiceProtocol)
        DependencyContainer.shared.register(MockGoalAlertService() as GoalAlertServiceProtocol)
        DependencyContainer.shared.register(MockIntegrationService() as IntegrationServiceProtocol)
        DependencyContainer.shared.register(MockHealthKitServiceForIntegrations() as HealthKitServiceProtocol)
        DependencyContainer.shared.register(MockScaleService() as ScaleServiceProtocol)
        DependencyContainer.shared.register(MockWifiScaleService() as WifiScaleServiceProtocol)
        DependencyContainer.shared.register(MockPushNotificationService() as PushNotificationServiceProtocol)
        DependencyContainer.shared.register(MockContentViewModelAccountFlagService() as AccountFlagServiceProtocol)
    }

    @discardableResult
    static func registerDashboardConcreteDependencies(
        performInitialAccountLoad: Bool = false
    ) -> DashboardConcreteDependencies {
        DependencyContainer.shared.register(KvStorageService.shared as KvStorageService)

        let accountService = AccountService(performInitialLoad: performInitialAccountLoad)
        let loggerService = LoggerService()
        let scaleService = ScaleService(accountService: accountService)
        let entryService = EntryService(accountService: accountService)

        DependencyContainer.shared.register(accountService as AccountService)
        DependencyContainer.shared.register(loggerService as LoggerService)
        DependencyContainer.shared.register(scaleService as ScaleService)
        DependencyContainer.shared.register(entryService as EntryService)

        return (
            account: accountService,
            logger: loggerService,
            scale: scaleService,
            entry: entryService
        )
    }

    static func registerBase(
        logger: LoggerServiceProtocol,
        keychain: KeychainServiceProtocol,
        bluetooth: BluetoothServiceProtocol
    ) {
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)
        DependencyContainer.shared.register(keychain as KeychainServiceProtocol)
        DependencyContainer.shared.register(bluetooth as BluetoothServiceProtocol)
    }
}
