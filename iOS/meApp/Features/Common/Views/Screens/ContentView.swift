//
//  ContentView.swift
//  meApp
//
//  Created by Barath Chittibabu on 27/05/25.
//

import SwiftUI
import ggInAppMessagingPackage

struct ContentView: View {
    @EnvironmentObject var themeManager: Theme
    @Environment(\.appTheme) private var theme
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var viewModel = ContentViewModel()
    
    var body: some View {
        VStack {
            switch viewModel.contentViewState {
            case .initializing:
                LoadingScreen()
            case .dashboard:
                BottomTabBarView()
            case .landing:
                LandingScreen()
            }
        }
        .preferredColorScheme(themeManager.getPreferredAppearanceMode())
        .onChange(of: colorScheme, { oldValue, newValue in
            themeManager.syncWithSystemColorScheme(newValue)
        })
        .onAppear {
            viewModel.performAppInitialization()
            themeManager.syncWithSystemColorScheme(colorScheme)
        }
    }
}

#Preview {
    ContentView()
        .environmentObject(Theme.shared)
}
