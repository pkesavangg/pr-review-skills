import SwiftUI

// MARK: - AppSyncTabScreen
/// A dedicated tab screen that launches the App Sync camera scanner.
/// Hides the bottom tab bar while scanning and allows quick navigation to
/// the Manual Entry tab when the user taps the *Manual Entry* button.
struct AppSyncTabScreen: View {
    // Theme & navigation
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var tabViewModel: BottomTabBarViewModel

    // MARK: - Body
    var body: some View {
        ZStack {
            if tabViewModel.selectedTab == .appsync {
                // Full-screen camera/scanner view
                AppSyncScannerView(
                    onClose: {
                        tabViewModel.selectTab(.dash)
                    },
                    onManualEntry: {
                        tabViewModel.selectTab(.entry)
                    },
                    onScanned: { entry in
                        tabViewModel.selectTab(.dash)
                    }
                )
            }
        }
        .onChange(of: tabViewModel.selectedTab, { oldValue, newValue in
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
