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
    var body: some View {
        VStack(spacing: 32) {
            if viewModel.isInitializing {
                LoadingPageView()
            } else if viewModel.showDashboardText {
                    Text(viewModel.dashboardTextView())
                        .fontOpenSans(.body1)
                        .foregroundColor(theme.textHeading)
                
            } else if viewModel.showLandingText {
                    Text(viewModel.landingTextView())
                        .fontOpenSans(.body1)
                        .foregroundColor(theme.textHeading)
            }
        }
        .task {
            await viewModel.performAppInitialization()
        }
    }
}
#Preview {
    ContentView()
        .environmentObject(Theme.shared)
}
