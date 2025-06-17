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
                    // TODO: remove these later. implemented for testing purpose.
                    VStack{
                        Text(viewModel.dashboardTextView())
                            .fontOpenSans(.body1)
                            .foregroundColor(theme.textHeading)
                        
                        ButtonView(text: "", type: .primary, size: .regular, isDisabled: false, action:{
                            Task { await viewModel.logout()
                            }})
                        
                    }
                    
                } else if viewModel.showLandingView {
                    LandingScreen()
                }
            }
            .task {
                await viewModel.performAppInitialization()
            }
        }.environmentObject(router)

    }
}

#Preview {
    ContentView()
        .environmentObject(Theme.shared)
}
