//
//  meAppApp.swift
//  meApp
//
//  Created by Barath Chittibabu on 27/05/25.
//

import SwiftUI

@main
struct meApp: App {
    // Initialize the ThemeManager to manage the app's theme
    let themeManager = Theme.shared
    // Initialize the ServiceRegistry to register the essential services
    let serviceRegistry = ServiceRegistry.shared
    var body: some Scene {
        WindowGroup {
            ContentView()
                .themeable()
                .environmentObject(themeManager)
        }
    }
}
