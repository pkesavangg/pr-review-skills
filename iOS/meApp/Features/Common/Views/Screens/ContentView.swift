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
#if DEBUG
                    VStack{
                        WeightTrendView()
                            .padding(.horizontal)
                        
                        ButtonView(text: "Logout", type: .primary, size: .regular, isDisabled: false, action:{
                            Task { await viewModel.logout()
                            }})
                        .padding(.top)
                        
                    }
                    
#endif
                    
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
