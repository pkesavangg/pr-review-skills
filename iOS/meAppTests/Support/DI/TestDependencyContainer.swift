import Foundation
@testable import meApp

@MainActor
enum TestDependencyContainer {
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
        DependencyContainer.shared.register(MockAccountService() as AccountServiceProtocol)
        DependencyContainer.shared.register(MockEntryService() as EntryServiceProtocol)
        DependencyContainer.shared.register(MockContentViewModelFeedService() as FeedServiceProtocol)
        DependencyContainer.shared.register(MockGoalAlertService() as GoalAlertServiceProtocol)
        DependencyContainer.shared.register(MockIntegrationService() as IntegrationServiceProtocol)
        DependencyContainer.shared.register(MockHealthKitServiceForIntegrations() as HealthKitServiceProtocol)
        DependencyContainer.shared.register(MockScaleService() as ScaleServiceProtocol)
        DependencyContainer.shared.register(MockContentViewModelAccountFlagService() as AccountFlagServiceProtocol)
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
