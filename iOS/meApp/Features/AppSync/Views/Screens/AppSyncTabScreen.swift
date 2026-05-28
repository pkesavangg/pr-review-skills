//
//  AppSyncTabScreen.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 08/07/25.
//

import AppSyncPackage
import SwiftUI

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
    // Gates scanner rendering until zoom is loaded for the active account.
    @State private var isScannerReady = false

    // MARK: - Body
    var body: some View {
        ZStack {
            if isScannerReady {
                // Full-screen camera/scanner view
                AppSyncScannerView(
                    onClose: {
                        isScannerReady = false
                        tabViewModel.restorePreviousTab()
                    },
                    onManualEntry: {
                        isScannerReady = false
                        tabViewModel.selectTab(.entry)
                    },
                    onScanned: { data in
                        isScannerReady = false
                        tabViewModel.restorePreviousTab()
                        scanStore.handleScanned(data, tabViewModel: tabViewModel)
                    }
                )
            }
        }
        .onChange(of: tabViewModel.selectedTab) { _, newValue in
            withAnimation {
                tabViewModel.showTabBar = newValue != .appsync
            }
        }
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
