//
//  ServiceRegistry.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//

import Foundation

/// A singleton class responsible for registering and deregistering services
/// within the application's dependency container.
///
/// This class supports two levels of service registration:
/// 1. **Essential Services** – registered at app launch (e.g., `AccountService`)
/// 2. **Session Services** – registered after login and deregistered on logout (e.g., `GraphService`)
///
/// Use this registry to ensure all services are properly managed within the app lifecycle.
@MainActor
class ServiceRegistry {
    static let shared = ServiceRegistry()

    init() {
        registerEssentialServices()
    }

    /// Registers services required at app launch (before login)
    @MainActor private func registerEssentialServices() {
        registerCoreInfrastructure()
        registerAccountDataAndScale()

        let notifications = NotificationHelperService.shared
        DependencyContainer.shared.register(notifications)
        DependencyContainer.shared.register(notifications as NotificationHelperServiceProtocol)

        let feed = FeedService.shared
        DependencyContainer.shared.register(feed)
        DependencyContainer.shared.register(feed as FeedServiceProtocol)

        let bluetooth = BluetoothService.shared
        DependencyContainer.shared.register(bluetooth)
        DependencyContainer.shared.register(bluetooth as BluetoothServiceProtocol)

        let permissions = PermissionsService.shared
        DependencyContainer.shared.register(permissions)
        DependencyContainer.shared.register(permissions as PermissionsServiceProtocol)

        let wifi = WifiScaleService.shared
        DependencyContainer.shared.register(wifi)
        DependencyContainer.shared.register(wifi as WifiScaleServiceProtocol)

        let integration = IntegrationsService.shared
        DependencyContainer.shared.register(integration)
        DependencyContainer.shared.register(integration as IntegrationServiceProtocol)

        let healthKit = HealthKitService.shared
        DependencyContainer.shared.register(healthKit)
        DependencyContainer.shared.register(healthKit as HealthKitServiceProtocol)

        let goalAlert = GoalAlertService.shared
        DependencyContainer.shared.register(goalAlert)
        DependencyContainer.shared.register(goalAlert as GoalAlertServiceProtocol)

        let accountFlag = AccountFlagService.shared
        DependencyContainer.shared.register(accountFlag)
        DependencyContainer.shared.register(accountFlag as AccountFlagServiceProtocol)

        let appReview = AppReviewService.shared
        DependencyContainer.shared.register(appReview)

        let push = PushNotificationService.shared
        DependencyContainer.shared.register(push)
        DependencyContainer.shared.register(push as PushNotificationServiceProtocol)

        let httpClient = HTTPClient.shared
        DependencyContainer.shared.register(httpClient)
        DependencyContainer.shared.register(httpClient as HTTPClientProtocol)
    }

    @MainActor private func registerCoreInfrastructure() {
        let logger = LoggerService.shared
        DependencyContainer.shared.register(logger)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)

        let kv = KvStorageService.shared
        DependencyContainer.shared.register(kv)
        DependencyContainer.shared.register(kv as KvStorageServiceProtocol)

        let keychain = KeychainService.shared
        DependencyContainer.shared.register(keychain)
        DependencyContainer.shared.register(keychain as KeychainServiceProtocol)
    }

    @MainActor private func registerAccountDataAndScale() {
        let account = AccountService.shared
        DependencyContainer.shared.register(account)
        DependencyContainer.shared.register(account as AccountServiceProtocol)

        let entry = EntryService.shared
        DependencyContainer.shared.register(entry)
        DependencyContainer.shared.register(entry as EntryServiceProtocol)

        let scale = ScaleService.shared
        DependencyContainer.shared.register(scale)
        DependencyContainer.shared.register(scale as ScaleServiceProtocol)

        let baby = BabyService.shared
        DependencyContainer.shared.register(baby)
        DependencyContainer.shared.register(baby as BabyServiceProtocol)
    }

    /// Registers services needed after login.
    ///
    /// `FeedService` previously lived here AND in `registerEssentialServices`,
    /// which left a race window during logout/re-login where `FeedService`
    /// could be unregistered between `AccountService`'s and
    /// `ContentViewModel`'s `activeAccount` sinks — causing the
    /// `Injector<FeedService>` resolve to return nil and the app to crash on
    /// `performAppInitialization`. Keeping `FeedService` only in the
    /// essential list closes that window. See MA-3897.
    @MainActor func registerSessionServices() {
        // MA-3897: FeedService is registered only in registerEssentialServices.
        // Do NOT re-register it here — doing so reopens the logout/re-login race
        // window where Injector<FeedService> resolves to nil and the app crashes
        // in performAppInitialization.
        let productTypeStore = ProductTypeStore.shared
        DependencyContainer.shared.register(productTypeStore)
        DependencyContainer.shared.register(productTypeStore as ProductTypeStoreProtocol)
    }

    /// Deregisters essential services (called during deinit or app shutdown)
    nonisolated private func deregisterEssentialServices() {
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: KeychainService.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: KeychainServiceProtocol.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: AccountService.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: AccountServiceProtocol.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: ScaleService.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: ScaleServiceProtocol.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: IntegrationsService.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: IntegrationServiceProtocol.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: HealthKitService.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: HealthKitServiceProtocol.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: KvStorageService.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: KvStorageServiceProtocol.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: LoggerService.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: LoggerServiceProtocol.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: NotificationHelperService.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: NotificationHelperServiceProtocol.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: EntryService.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: EntryServiceProtocol.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: FeedService.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: FeedServiceProtocol.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: BluetoothService.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: BluetoothServiceProtocol.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: PermissionsService.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: PermissionsServiceProtocol.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: WifiScaleService.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: WifiScaleServiceProtocol.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: GoalAlertService.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: GoalAlertServiceProtocol.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: AccountFlagService.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: AccountFlagServiceProtocol.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: AppReviewService.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: HTTPClient.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: PushNotificationServiceProtocol.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: PushNotificationService.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: HTTPClientProtocol.self))
    }

    /// Deregisters session-level services (call during logout or deinit).
    ///
    /// Currently a no-op — see `registerSessionServices` for context.
    nonisolated func deregisterSessionServices() {
        // MA-3897: do NOT deregister FeedService here — it is an essential-tier
        // service and tearing it down on logout caused the init crash.
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: ProductTypeStore.self))
        DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: ProductTypeStoreProtocol.self))
    }

    deinit {
        self.deregisterEssentialServices()
        self.deregisterSessionServices()
    }
}

// MARK: - Usage Example for Dependency Injection

/*
To use the dependency injection system:

1. Register essential services at app launch:

   @MainActor
   final class AccountService {
       var currentUserId: String? = nil
   }

   // This is automatically handled by ServiceRegistry.shared at app launch
   ServiceRegistry.shared

2. Register session-specific services after successful login:

   await ServiceRegistry.shared.registerSessionServices()

3. Inject the service using the @Injector property wrapper:

   @MainActor
   @Observable
   final class LoginStore {
       @ObservationIgnored
       @Injector private var accountService: AccountService

       init() {
           // e.g. LoggerService.shared.log(level: .debug, tag: "LoginStore", message: "currentUserId", data: accountService.currentUserId)
       }
   }

4. Deregister session services during logout:

   ServiceRegistry.shared.deregisterSessionServices()

Notes:
- All dependencies are resolved by type name.
- Make sure session services are registered before they're injected or accessed.
- Use @MainActor for any service that interacts with UI or must run on the main thread.
*/
