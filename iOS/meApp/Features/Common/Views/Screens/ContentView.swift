//
//  ContentView.swift
//  meApp
//
//  Created by Barath Chittibabu on 27/05/25.
//

import SwiftUI

struct ContentView: View {
    @EnvironmentObject var themeManager: Theme
    @Environment(\.appTheme) private var theme
    @Environment(\.colorScheme) private var colorScheme
    @State private var isLogoAnimated = false
    
    var body: some View {
        VStack(spacing: 32) {
            LoadingScreen()
        }
    }
}

// MARK: - Preview
#Preview {
    ContentView()
        .environmentObject(Theme.shared)
}
