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
                
                settingsSection()
                
                if scaleType == .bluetoothR4 {
                    connectionSection()
                }
                
                supportSection()
                deleteScaleSection()
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
        }
        .inAppBrowser(
            url: scaleStore.presentingBrowserURL,
            isPresented: scaleStore.isBrowserPresented
        )
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
        }
        .settingsRowInsets()
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
        }
        .settingsRowInsets()
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    private func settingsSection() -> some View {
        Section(header: SectionHeader(title: lang.settingsSectionHeader)) {
            if scaleType == .bluetoothR4 {
                SettingsListItem(
                    config: SettingsItemConfig(
                        title: lang.mode,
                        value: scaleStore.modeValue,
                        onTap: { scaleStore.modeTapped() }
                    )
                )
                SettingsListItem(
                    config: SettingsItemConfig(
                        title: lang.displayMetrics,
                        value: scaleStore.displayMetricsValue,
                        onTap: { scaleStore.displayMetricsTapped() }
                    )
                )
                SettingsListItem(
                    config: SettingsItemConfig(
                        title: lang.users,
                        value: scaleStore.usersValue,
                        onTap: { scaleStore.usersTapped() }
                    )
                )
            }
            SettingsListItem(
                config: SettingsItemConfig(
                    title: lang.scaleName,
                    value: scaleStore.scaleNameValue,
                    onTap: { scaleStore.scaleNameTapped() }
                )
            )
        }
        .settingsRowInsets()
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    private func connectionSection() -> some View {
        Section(header: SectionHeader(title: lang.connectionSectionHeader)) {
            SettingsListItem(
                config: SettingsItemConfig(
                    title: lang.bluetooth,
                    value: scaleStore.bluetoothValue,
                    onTap: { scaleStore.bluetoothTapped() }
                )
            )
            SettingsListItem(
                config: SettingsItemConfig(
                    title: lang.wifi,
                    value: scaleStore.wifiValue,
                    onTap: { scaleStore.wifiTapped() }
                )
            )
            SettingsListItem(
                config: SettingsItemConfig(
                    title: lang.wifiMacAddress,
                    value: scaleStore.wifiMacAddressValue,
                    onTap: { scaleStore.wifiMacAddressTapped() }
                )
            )
        }
        .settingsRowInsets()
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    private func supportSection() -> some View {
        Section(header: SectionHeader(title: lang.supportSectionHeader)) {
            SettingsListItem(
                config: SettingsItemConfig(
                    title: lang.scaleType,
                    value: scaleStore.scaleTypeValue,
                    onTap: { scaleStore.scaleTypeTapped() }
                )
            )
            SettingsListItem(
                config: SettingsItemConfig(
                    title: lang.sku.uppercased(),
                    value: scaleStore.skuValue,
                    canShowChevron: false
                )
            )
            SettingsListItem(
                config: SettingsItemConfig(
                    title: lang.datePaired,
                    value: scaleStore.datePairedValue,
                    canShowChevron: false
                )
            )
            SettingsListItem(
                config: SettingsItemConfig(
                    title: lang.productGuide,
                    onTap: { scaleStore.openProductGuide(for: scaleStore.skuValue) }
                )
            )
        }
        .settingsRowInsets()
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
}
