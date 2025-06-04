//
//  meAppApp.swift
//  meApp
//
//  Created by Barath Chittibabu on 27/05/25.
//

import SwiftUI

@main
struct meApp: App {
    /// The shared app state, injected as an environment object to enable global service access.
    private var appState = AppState()
    // Register app delegate for Firebase setup
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .themeable()
                .environmentObject(appState.themeManager)
        }
    }
}
