//
//  meAppApp.swift
//  meApp
//
//  Created by Barath Chittibabu on 27/05/25.
//

import SwiftUI
import SwiftData

@main
struct meApp: App {
    let themeManager = Theme.shared
    var body: some Scene {
        WindowGroup {
            ContentView()
                .themeable()
                .environmentObject(themeManager)
        }
    }
}
