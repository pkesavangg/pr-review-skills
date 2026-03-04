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
        DependencyContainer.shared.register(MockEntryService() as EntryServiceProtocol)
        DependencyContainer.shared.register(MockGoalAlertService() as GoalAlertServiceProtocol)
        DependencyContainer.shared.register(MockIntegrationService() as IntegrationServiceProtocol)
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
