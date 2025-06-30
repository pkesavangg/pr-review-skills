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
    
    private let lang = PermissionsStrings.self
    
    // Dummy permission values (placeholder until real permission fetching is implemented)
    @State var bluetoothAuthorized      = true
    @State var bluetoothPowerOn         = true
    @State var locationServicesEnabled  = true
    @State var locationAuthorized       = false
    @State var cameraAuthorized         = true
    @State var notificationsEnabled     = true
    
    var body: some View {
        // TODO: Need to bind the actual permission values to the state variables above and update the icons accordingly.
        VStack(spacing: 0) {
            NavbarHeaderView<Image, EmptyView>(
                title: lang.title,
                leadingContent: { Image(AppAssets.chevronLeft) },
                onLeadingTap: { router.navigateBack() },
                canShowBorder: true
            )
            ZStack {
                theme.backgroundSecondary.ignoresSafeArea()
                List {
                    bluetoothSection
                    locationSection
                    cameraSection
                    notificationSection
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
            }
        }
        .navigationBarBackButtonHidden(true)
    }
    
    // MARK: Sections
    private var bluetoothSection: some View {
        Section(header: sectionHeader(lang.bluetooth)) {
            // Access authorised
            ActionListItemView(config: ActionListItemConfig(
                title: lang.bluetoothAccessAuthorized,
                chevronType: .none,
                leadingIcon: AnyView(AppIconView(icon: AppAssets.checkMarkCircle)
                                    .foregroundColor(theme.statusIconPrimary)
                )
            ))
            .listRowInsets()
            
            // BT powered on
            ActionListItemView(config: ActionListItemConfig(
                title: lang.bluetoothTurnedOn,
                chevronType: .none,
                leadingIcon: AnyView(AppIconView(icon: AppAssets.checkMarkCircle)
                                    .foregroundColor(theme.statusIconPrimary)
                )
            ))
            .listRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    
    private var locationSection: some View {
        Section(header: sectionHeader(lang.location)) {
            ActionListItemView(config: ActionListItemConfig(
                title: lang.locationAccessEnabled,
                chevronType: .none,
                leadingIcon: AnyView(AppIconView(icon: AppAssets.checkMarkCircle)
                                    .foregroundColor(theme.statusIconPrimary)
                )
            ))
            .listRowInsets()
            
            ActionListItemView(config: ActionListItemConfig(
                title: lang.locationAccessNotAuthorized,
                chevronType: .none,
                leadingIcon: AnyView(AppIconView(icon: AppAssets.checkMarkCircle)
                                    .foregroundColor(theme.statusIconPrimary)
                )
            ))
            .listRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    
    private var cameraSection: some View {
        Section(header: sectionHeader(lang.camera)) {
            ActionListItemView(config: ActionListItemConfig(
                title: lang.cameraAccessAuthorized,
                chevronType: .none,
                leadingIcon: AnyView(AppIconView(icon: AppAssets.checkMarkCircle)
                                    .foregroundColor(theme.statusIconPrimary)
                )
            ))
            .listRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    
    private var notificationSection: some View {
        Section(header: sectionHeader(lang.notifications)) {
            ActionListItemView(config: ActionListItemConfig(
                title: lang.notificationsEnabled,
                chevronType: .none,
                leadingIcon: AnyView(AppIconView(icon: AppAssets.checkMarkCircle)
                                    .foregroundColor(theme.statusIconPrimary)
                )
            ))
            .listRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    
    // MARK: Helpers
    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .fontOpenSans(.heading5)
            .foregroundColor(theme.textHeading)
            .textCase(.none)
            .padding(.bottom, .spacingXS)
            .padding(.leading, -16)
    }
}

#Preview {
    AppPermissionsScreen()
        .environmentObject(SettingsStore())
        .environmentObject(BottomTabBarViewModel())
        .environmentObject(Router<SettingsRoute>())
}
