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
    @StateObject private var viewModel = ContentViewModel()
    @State private var isLogoAnimated = false
    @StateObject private var router = Router<AuthRoute>()
    
    var body: some View {
        RoutingView(stack: $router.stack) {
            VStack(spacing: 32) {
                if viewModel.isInitializing {
                    LoadingScreen()
                } else if viewModel.showDashboardView {
                        Text(viewModel.dashboardTextView())
                            .fontOpenSans(.body1)
                            .foregroundColor(theme.textHeading)
                    
                } else if viewModel.showLandingView {
                    LandingScreen()
                }
            }
            .preferredColorScheme(themeManager.getPreferredAppearanceMode())
            .onChange(of: colorScheme, { oldValue, newValue in
                themeManager.syncWithSystemColorScheme(newValue)
            })
            .task {
                await viewModel.performAppInitialization()
                themeManager.syncWithSystemColorScheme(colorScheme)
            }
        }.environmentObject(router)

    }
}

#Preview {
    ContentView()
        .environmentObject(Theme.shared)
}
