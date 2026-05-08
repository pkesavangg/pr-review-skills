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
    @State private var didShowGraphScrollHint = false

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
            showGraphScrollHintForTesting()
        }
    }

    // TODO: Testing-only — show graph-scroll discoverability hint on every app
    // launch. Replace with a one-time UserDefaults gate before shipping.
    private func showGraphScrollHintForTesting() {
        guard !didShowGraphScrollHint else { return }
        didShowGraphScrollHint = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
            NotificationHelperService.shared.showModal(
                ModalData(
                    presentedView: AnyView(
                        GraphScrollHintModalView {
                            NotificationHelperService.shared.dismissModal()
                        }
                    ),
                    backdropDismiss: true
                )
            )
        }
    }
}

#Preview {
    ContentView()
        .environmentObject(Theme.shared)
}
