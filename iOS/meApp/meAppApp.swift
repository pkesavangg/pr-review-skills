//
//  meAppApp.swift
//  meApp
//
//  Created by Barath Chittibabu on 27/05/25.
//
import SwiftUI

// MARK: - meApp
/// Entry point of the app using the SwiftUI `@main` attribute.
/// We use `@UIApplicationDelegateAdaptor` to delegate control to `AppDelegate`,
/// which in turn configures `SceneDelegate` for manual window and view management.
/// The main `ContentView` is created and injected inside `SceneDelegate`, not here.
/// `EmptyView()` is returned to satisfy SwiftUI’s requirement for a scene while
/// deferring actual UI rendering and dependency injection to the UIKit layer.
@main
struct meApp: App {
    /// The shared app state, injected as an environment object to enable global service access.
    private var appState = AppState()
    // Register app delegate for Firebase setup
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    
    var body: some Scene {
        WindowGroup {
            EmptyView()
        }
    }
}
