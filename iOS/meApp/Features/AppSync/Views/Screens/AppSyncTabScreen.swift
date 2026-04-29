//
//  AppSyncTabScreen.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 08/07/25.
//

import SwiftUI
import AppSyncPackage

// MARK: - AppSyncTabScreen
/// A dedicated tab screen that launches the App Sync camera scanner.
/// Hides the bottom tab bar while scanning and allows quick navigation to
/// the Manual Entry tab when the user taps the *Manual Entry* button.
struct AppSyncTabScreen: View {
    // Theme & navigation
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var tabViewModel: BottomTabBarViewModel
    @Injector private var logger: LoggerService
    // Store handling scan results
    @StateObject private var scanStore = AppSyncTabStore()
    // Forces a fresh camera/scanner instance whenever AppSync tab is re-opened.
    @State private var scannerSessionId = UUID()
    private let tag = "AppSyncTabScreen"

    // MARK: - Body
    var body: some View {
        ZStack {
            if tabViewModel.selectedTab == .appsync {
                // Full-screen camera/scanner view
                AppSyncScannerView(
                    initialZoom: scanStore.initialZoom,
                    onClose: {
                        scannerSessionId = UUID()
                        tabViewModel.restorePreviousTab()
                    },
                    onManualEntry: {
                        scannerSessionId = UUID()
                        tabViewModel.selectTab(.entry)
                    },
                    onScanned: { data in
                        scannerSessionId = UUID()
                        tabViewModel.restorePreviousTab()
                        scanStore.handleScanned(data, tabViewModel: tabViewModel)
                    }
                )
                .id(scannerSessionId)
            }
        }
        .onChange(of: tabViewModel.selectedTab, { oldValue, newValue in
            if newValue == .appsync {
                scanStore.loadSavedZoom()
            }
            withAnimation {
                tabViewModel.showTabBar = newValue != .appsync
            }
        })
    }
}

#if DEBUG
struct AppSyncTabScreen_Previews: PreviewProvider {
    static var previews: some View {
        AppSyncTabScreen()
            .environmentObject(BottomTabBarViewModel())
            .environmentObject(Theme.shared)
    }
}
#endif 
