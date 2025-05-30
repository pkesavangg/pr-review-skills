//
//  RoutingView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//

//  MARK: - RoutingView
//  A reusable SwiftUI wrapper around NavigationStack for dynamic, stack-based routing.
//  - `Root`: the root view.
//  - `Routes`: destination views conforming to `Routable` (View + Hashable).
//  Bind it to a router's stack for declarative, programmatic navigation.
//

import SwiftUI

public struct RoutingView<Root: View, Routes: Routable>: View {
    @Binding private var routes: [Routes]
    private let root: () -> Root
    
    public init(
        stack: Binding<[Routes]>,
        @ViewBuilder root: @escaping () -> Root
    ) where Routes: Routable {
        self._routes = stack
        self.root = root
    }
    public var body: some View {
        NavigationStack(path: $routes) {
            root()
                .navigationDestination(for: Routes.self) { view in
                    view
                }
        }
    }
}

// MARK: - Usage Guide

/*
 ## SwiftUI Router System Usage Guide
 
 ### 1. Define Your Routes
 Create an enum conforming to `Routable`:
 ```swift
 enum AppRoute: Routable {
 case home
 case profile
 case settings
 
 var body: some View {
 switch self {
 case .home: HomeView()
 case .profile: ProfileView()
 case .settings: SettingsView()
 }
 }
 }
 ```
 
 ### 2. Set Up Your Router
 Create a router instance in your main view:
 ```swift
 @StateObject private var router = Router<AppRoute>()
 ```
 
 ### 3. Wrap Your Root View
 Use `RoutingView` to enable navigation:
 ```swift
 RoutingView(stack: $router.stack) {
 HomeView()
 }
 .environmentObject(router)
 ```
 
 ### 4. Navigate in Your Views
 Access the router via environment and use navigation methods:
 ```swift
 @EnvironmentObject var router: Router<AppRoute>
 
 // Navigate to a single destination
 router.navigate(to: .profile)
 
 // Navigate to multiple destinations
 router.navigate(to: [.profile, .settings])
 
 // Navigate back
 router.navigateBack()
 router.navigateBack(2) // Go back 2 steps
 router.navigateBack(to: .home) // Go back to specific destination
 
 // Replace navigation stack
 router.replace(with: [.profile])
 
 // Return to root
 router.navigateToRoot()
 ```
 
 ### Key Features:
 - Type-safe navigation with compile-time route validation
 - Programmatic navigation control
 - Support for deep linking and complex navigation flows
 - Integration with SwiftUI's NavigationStack
 - Observable navigation state for reactive UI updates
 - Comprehensive stack management operations
 */
