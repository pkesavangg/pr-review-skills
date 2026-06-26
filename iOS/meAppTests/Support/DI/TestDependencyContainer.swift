import Foundation
@testable import meApp

@MainActor
enum TestDependencyContainer {
    struct DashboardConcreteDependencies {
        let account: AccountService
        let logger: LoggerService
        let scale: DeviceService
        let entry: EntryService
        let accountLocalRepo: MockAccountRepository
    }

    static func reset() {
        DependencyContainer.shared.dependencies.removeAll()
        // Keep core injected protocols available even before per-suite overrides are registered.
        registerBase(
            logger: MockLoggerService(),
            keychain: MockKeychainService(),
            bluetooth: MockBluetoothService()
        )
        DependencyContainer.shared.register(KvStorageService.shared as KvStorageServiceProtocol)
        let notification = MockNotificationHelperService()
        DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
        DependencyContainer.shared.register(MockAccountService() as AccountServiceProtocol)
        DependencyContainer.shared.register(MockBabyService() as BabyServiceProtocol)
        DependencyContainer.shared.register(MockEntryService() as EntryServiceProtocol)
        DependencyContainer.shared.register(MockContentViewModelFeedService() as FeedServiceProtocol)
        DependencyContainer.shared.register(MockGoalAlertService() as GoalAlertServiceProtocol)
        DependencyContainer.shared.register(MockIntegrationService() as IntegrationServiceProtocol)
        DependencyContainer.shared.register(MockHealthKitServiceForIntegrations() as HealthKitServiceProtocol)
        DependencyContainer.shared.register(MockScaleService() as PairedDeviceServiceProtocol)
        DependencyContainer.shared.register(MockWifiScaleService() as WifiPairedDeviceServiceProtocol)
        DependencyContainer.shared.register(MockPushNotificationService() as PushNotificationServiceProtocol)
        // The real PushNotificationService.shared singleton (lazily created when AccountService /
        // ServiceRegistry touch it) starts NWPathMonitor network monitoring, whose async callback
        // resolves `permissionsService` via @Injector. Without a registered PermissionsServiceProtocol
        // here, that leaked callback hits @Injector's fatalError mid-suite — crashing the runner at a
        // random test and producing the shifting "TEST EXECUTE FAILED" failures. Register a permissions
        // mock so the leaked monitor resolves cleanly.
        let permissions = MockPermissionsService()
        DependencyContainer.shared.register(permissions)
        DependencyContainer.shared.register(permissions as PermissionsServiceProtocol)
        DependencyContainer.shared.register(MockContentViewModelAccountFlagService() as AccountFlagServiceProtocol)
        DependencyContainer.shared.register(MockKvStorageService() as KvStorageServiceProtocol)
        DependencyContainer.shared.register(MockBabyService() as BabyServiceProtocol)
        let mockProductTypeStore = MockProductTypeStore()
        DependencyContainer.shared.register(mockProductTypeStore as ProductTypeStoreProtocol)
        DependencyContainer.shared.register(mockProductTypeStore)
        // Also register under the concrete class name for direct injection
        DependencyContainer.shared.dependencies["ProductTypeStore"] = mockProductTypeStore
        DependencyContainer.shared.dependencies["meApp.ProductTypeStore"] = mockProductTypeStore
        // Some stores/managers inject concrete dashboard services.
        // Register mock-backed concrete instances to keep tests isolated and avoid DI fatals.
        _ = registerDashboardConcreteDependencies()
    }

    @discardableResult
    static func registerDashboardConcreteDependencies(
        performInitialAccountLoad: Bool = false
    ) -> DashboardConcreteDependencies {
        DependencyContainer.shared.register(KvStorageService.shared as KvStorageService)

        let accountLocalRepo = MockAccountRepository()
        let accountService = AccountService(
            apiRepo: MockAccountAPIRepository(),
            localRepo: accountLocalRepo,
            integrationApiRepo: MockIntegrationAPIRepository(),
            networkMonitor: MockNetworkMonitor(isConnected: true),
            performInitialLoad: performInitialAccountLoad
        )
        let loggerService = LoggerService()
        let scaleService = DeviceService(
            accountService: accountService,
            apiRepository: MockScaleRepositoryAPI(),
            localRepository: MockScaleRepository()
        )
        let entryService = EntryService(
            accountService: accountService,
            localRepo: MockEntryRepository(),
            localKVRepo: MockEntrySyncStore(),
            remoteRepo: MockEntryRepositoryAPI()
        )
        let goalAlertService = GoalAlertService(
            notificationService: MockNotificationHelperService(),
            accountService: accountService,
            bluetoothService: MockBluetoothService(),
            logger: MockLoggerService(),
            kv: MockKvStorageService(),
            setGoalModalDelay: 0
        )

        DependencyContainer.shared.register(accountService as AccountService)
        DependencyContainer.shared.register(loggerService as LoggerService)
        DependencyContainer.shared.register(scaleService as DeviceService)
        DependencyContainer.shared.register(entryService as EntryService)
        DependencyContainer.shared.register(goalAlertService as GoalAlertService)
        DependencyContainer.shared.register(ProductTypeStore.shared as ProductTypeStore)

        return DashboardConcreteDependencies(
            account: accountService,
            logger: loggerService,
            scale: scaleService,
            entry: entryService,
            accountLocalRepo: accountLocalRepo
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
