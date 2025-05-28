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
        // Example placeholder registration:
        // DependencyContainer.shared.register(AccountService())
        //
        // Note: AccountService is not implemented yet.
        // This line is included as a reference for how to register essential services.
    }
    
    /// Registers services needed after login
    @MainActor func registerSessionServices() {
        // Example placeholder registration:
        // DependencyContainer.shared.register(GraphService())
        //
        // Note: GraphService is not implemented yet.
        // This line is included as a reference for how to register session-scoped services.
        // Add other post-login services here as needed.
    }

    /// Deregisters essential services (called during deinit or app shutdown)
    nonisolated private func deregisterEssentialServices() {
        // Example placeholder deregistration:
        // DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: AccountService.self))
        //
        // Note: AccountService is not implemented yet.
        // This line is included as a reference for how to clean up essential services.
    }

    /// Deregisters session-level services (call during logout or deinit)
    nonisolated func deregisterSessionServices() {
        // Example placeholder deregistration:
        // DependencyContainer.shared.dependencies.removeValue(forKey: String(describing: GraphService.self))
        //
        // Note: GraphService is not implemented yet.
        // This line is included as a reference for how to clean up session-scoped services.
        // Remove other post-login services here as needed.
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
           print(accountService.currentUserId)
       }
   }

4. Deregister session services during logout:

   ServiceRegistry.shared.deregisterSessionServices()

Notes:
- All dependencies are resolved by type name.
- Make sure session services are registered before they're injected or accessed.
- Use @MainActor for any service that interacts with UI or must run on the main thread.
*/


