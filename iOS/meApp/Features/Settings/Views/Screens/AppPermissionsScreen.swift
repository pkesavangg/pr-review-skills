//
//  AppPermissionsScreen.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 24/06/25.
//

import SwiftUI

// MARK: - AppPermissionsScreen
/// Displays the current permission & service states required by the application (Bluetooth, Location, Camera, Notifications).
/// The view mirrors the UX of other settings sub-screens and uses `ActionListItemView` for each row.
struct AppPermissionsScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var tabViewModel: BottomTabBarViewModel
    @EnvironmentObject private var settingsStore: SettingsStore
    @EnvironmentObject var router: Router<SettingsRoute>
    /// Store that derives which permission sections must be displayed.
    @StateObject private var permissionsStore = PermissionsStore()
    private let lang = PermissionsStrings.self
    
    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView<Image, EmptyView>(
                title: lang.title,
                leadingContent: { Image(AppAssets.chevronLeft) },
                onLeadingTap: {
                    router.navigateBack()
                },
                canShowBorder: true
            )
            ZStack {
                theme.backgroundSecondary.ignoresSafeArea()
                PermissionListView(
                    requiredCategories: permissionsStore.requiredCategories
                )
                .padding(.horizontal, .spacingSM)
            }
        }
        .navigationBarBackButtonHidden(true)
    }
}

#Preview {
    AppPermissionsScreen()
        .environmentObject(Theme.shared)
        .environmentObject(SettingsStore())
        .environmentObject(BottomTabBarViewModel())
        .environmentObject(Router<SettingsRoute>())
}
