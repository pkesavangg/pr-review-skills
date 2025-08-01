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
    
    // Timer for periodic connection status refresh
    @State private var connectionRefreshTimer: Timer?
    
    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: scale.nickname ?? scale.deviceName,
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
        .onAppear {
            // Set up navigation callback for WiFi setup
            scaleStore.onNavigateToWifi = {
                router.navigate(to: .wifi(scale: scale))
            }
            
            Task {
                await scaleStore.loadScale(scale)
                // Force refresh to ensure we have the latest data
                await scaleStore.forceRefreshDeviceData()
                
                // Refresh connection status specifically
                await scaleStore.refreshConnectionStatus()
                
                // Refresh WiFi status to ensure banner logic is correct
                await scaleStore.refreshWifiStatus()
                
                // Check device info and WiFi configuration for scale SKU 0412
                await scaleStore.checkDeviceInfoAndWifiConfiguration()
                
                // Note: Users will be fetched when the users button is tapped
            }
            
                Task {
                    await scaleStore.refreshConnectionStatus()
                    await scaleStore.refreshWifiStatus()
                    
                    // Periodically check device info and WiFi configuration for scale SKU 0412
                    if scale.sku == SettingsConstants.defaultR4Sku {
                        await scaleStore.checkDeviceInfoAndWifiConfiguration()
                    }
                }
        }
        .onDisappear {
            // Clean up timer
            connectionRefreshTimer?.invalidate()
            connectionRefreshTimer = nil
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
            // Refresh WiFi status when app becomes active (e.g., returning from WiFi setup)
            Task {
                await scaleStore.refreshWifiStatus()
                
                // Check device info and WiFi configuration for scale SKU 0412 when app becomes active
                if scale.sku == SettingsConstants.defaultR4Sku {
                    await scaleStore.checkDeviceInfoAndWifiConfiguration()
                }
            }
        }
    }
    
    // MARK: - Sections as Functions
    private func scaleImageSection() -> some View {
        let sku = scale.sku ?? ""
        let imagePath = SCALES.first(where: { $0.sku == sku })?.imgPath ?? AppAssets.scale0412 // fallback
        return Image(imagePath)
            .resizable()
            .scaledToFit()
            .frame(width: 180, height: 180)
            .frame(maxWidth: .infinity)
            .listRowBackground(Color.clear)
    }
    private func scaleStatusBannerSection() -> some View {
        Section {
            if scaleStore.shouldShowWeightOnlyBanner {
                ScaleStatusBanner(type: .weightOnly {
                    // Navigate to scale modes screen where user can change their mode
                    router.navigate(to: .scaleModes(scale: scale))
                })
            } else if scaleStore.shouldShowSetupIncompleteBanner {
                ScaleStatusBanner(type: .setupIncomplete {
                    scaleStore.handleSetupIncompleteBannerAction()
                })
            }
        }
        .listRowInsets()
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
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
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }
    
    private func settingsSection() -> some View {
        Section(header: SectionHeader(title: lang.settingsSectionHeader)) {
            if scaleType == .bluetoothR4 {
                ActionListItemView(
                    config: ActionListItemConfig(
                        title: lang.mode,
                        value: scaleStore.modeValue.rawValue,
                        onTap: {
                            router.navigate(to: .scaleModes(scale: scale))
                        }
                    )
                )
                ActionListItemView(
                    config: ActionListItemConfig(
                        title: lang.displayMetrics,
                        value: scaleStore.displayMetricsValue,
                        onTap: { router.navigate(to: .displayMetrics(scale: scale)) }
                    )
                )
                ActionListItemView(
                    config: ActionListItemConfig(
                        title: lang.users,
                        value: scaleStore.usersValue,
                        isDisabled: !scaleStore.isDeviceConnected,
                        onTap: { 
                            Task {
                                // Show loader while fetching users
                                scaleStore.showUsersLoader()
                                await scaleStore.fetchUserList()
                                // Hide loader and navigate
                                scaleStore.hideUsersLoader()
                                router.navigate(to: .users(scale: scale))
                            }
                        }
                    )
                )
            }
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.scaleName,
                    value: scaleStore.scale?.nickname ?? scale.deviceName ?? MyScaleStrings.unknownScale,
                    onTap: { router.navigate(to: .scaleNameScreen(scale: scale)) }
                )
            )
            
            if scaleType == .bluetoothA3 || scaleType == .bluetoothA6 {
                ActionListItemView(config: ActionListItemConfig(title: lang.userNumber, value: scale.userNumber))
            }
        }
        .listRowInsets()
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
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
                    isDisabled: !scaleStore.isDeviceConnected,
                    onTap: { router.navigate(to: .wifi(scale: scale)) }
                )
            )
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.wifiMacAddress,
                    value: scaleStore.wifiMacAddressValue,
                    isDisabled: !scaleStore.isDeviceConnected,
                    onTap: { router.navigate(to: .wifiMacAddress(scale: scale)) }
                )
            )
        }
        .listRowInsets()
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }
    
    private func supportSection() -> some View {
        Section(header: SectionHeader(title: lang.supportSectionHeader)) {
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.scaleType,
                    value: scaleStore.scaleTypeValue,
                    chevronType: .none,
                    onTap: {}
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
                    onTap: { scaleStore.openProductGuide(for: scale.sku ?? "") }
                )
            )
        }
        .listRowInsets()
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }
}
