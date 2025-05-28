//
//  meAppApp.swift
//  meApp
//
//  Created by Barath Chittibabu on 27/05/25.
//

import SwiftUI

@main
struct meApp: App {
    let themeManager = Theme.shared
    let serviceRegistry = ServiceRegistry.shared
    var body: some Scene {
        WindowGroup {
            ContentView()
                .themeable()
                .environmentObject(themeManager)
        }
    }
}
