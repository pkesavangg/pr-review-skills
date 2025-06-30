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
        .listRowInsets()
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    private func deleteScaleSection() -> some View {
        Section {
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.deleteScale,
                    chevronType: .none,
                    isDestructive: true,
                    onTap: {
                        scaleStore.handleScaleDelete(scaleId: scale.id) {
                            router.navigateBack(to: .addEditScales)
                        }
                    }
                )
            )
        }
        .listRowInsets()
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    
    private func settingsSection() -> some View {
        Section(header: SectionHeader(title: lang.settingsSectionHeader)) {
            if scaleType == .bluetoothR4 {
                ActionListItemView(
                    config: ActionListItemConfig(
                        title: lang.mode,
                        value: scaleStore.modeValue.rawValue,
                        onTap: {
                            router.navigate(to: .scaleModes)
                        }
                    )
                )
                ActionListItemView(
                    config: ActionListItemConfig(
                        title: lang.displayMetrics,
                        value: scaleStore.displayMetricsValue,
                        onTap: { router.navigate(to: .displayMetrics) }
                    )
                )
                ActionListItemView(
                    config: ActionListItemConfig(
                        title: lang.users,
                        value: scaleStore.usersValue,
                        onTap: { router.navigate(to: .users) }
                    )
                )
            }
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.scaleName,
                    value: scale.deviceName,
                    onTap: { router.navigate(to: .scaleNameScreen(scaleName: scale.deviceName ?? MyScaleStrings.unknownScale)) }
                )
            )
        }
        .listRowInsets()
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    
    private func connectionSection() -> some View {
        Section(header: SectionHeader(title: lang.connectionSectionHeader)) {
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.bluetooth,
                    value: scaleStore.bluetoothValue,
                    onTap: { router.navigate(to: .scaleBluetoothScreen(scale: scale)) }
                )
            )
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.wifi,
                    value: scaleStore.wifiValue,
                    onTap: { scaleStore.wifiTapped() }
                )
            )
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.wifiMacAddress,
                    value: scaleStore.wifiMacAddressValue,
                    onTap: { scaleStore.wifiMacAddressTapped() }
                )
            )
        }
        .listRowInsets()
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
    
    private func supportSection() -> some View {
        Section(header: SectionHeader(title: lang.supportSectionHeader)) {
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.scaleType,
                    value: scaleStore.scaleTypeValue,
                    onTap: { scaleStore.scaleTypeTapped() }
                )
            )
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.sku.uppercased(),
                    value: scaleStore.skuValue,
                    chevronType: .none
                )
            )
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.datePaired,
                    value: scaleStore.datePairedValue,
                    chevronType: .none
                )
            )
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.productGuide,
                    onTap: { scaleStore.openProductGuide(for: scaleStore.skuValue) }
                )
            )
        }
        .listRowInsets()
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtility)
    }
}
