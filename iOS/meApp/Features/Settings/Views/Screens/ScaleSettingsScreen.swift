//
//  ScaleSettingsScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 24/06/25.
//

import SwiftUI

struct ScaleSettingsScreen: View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    @ObservedObject var scaleStore = ScaleStore()
    let scale: Device
    var scaleType: ScaleType
    let lang = ScaleSettingsStrings.self
    
    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: scale.deviceName,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: { EmptyView() },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )
            
            List {
                scaleImageSection()
                
                if scaleType == .bluetoothR4 {
                    scaleStatusBannerSection()
                }
                
                deleteScaleSection()
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
    }
    
    // MARK: - Sections as Functions
    private func scaleImageSection() -> some View {
        Image(AppAssets.scale0412)
            .frame(width: 370)
            .frame(maxWidth: .infinity)
            .listRowBackground(Color.clear)
    }
    
    private func scaleStatusBannerSection() -> some View {
        Section {
            ScaleStatusBanner(type: .weightOnly {})
                .settingsRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    
    private func deleteScaleSection() -> some View {
        Section {
            SettingsListItem(
                config: SettingsItemConfig(
                    title: lang.deleteScale,
                    canShowChevron: false,
                    isDestructive: true,
                    onTap: {
                        scaleStore.handleScaleDelete(scaleId: scale.id) {
                            router.navigateBack(to: .addAndEditScales)
                        }
                    }
                )
            )
            .settingsRowInsets()
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
}
