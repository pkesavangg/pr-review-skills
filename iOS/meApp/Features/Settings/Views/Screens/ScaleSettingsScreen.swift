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
    @StateObject private var scaleSettingsStore: ScaleSettingsStore
    let scale: Device
    var scaleType: ScaleType
    @State private var isOtherSettingsSheetPresented = false
    @State private var isSoftwareUpdatePresented = false
    
    private var truncatedTitle: String {
        let title = scale.nickname ?? scale.deviceName ?? ""
        return title.count > 25 ? "\(title.prefix(25))…" : title
    }
    
    init(scale: Device, scaleType: ScaleType) {
        self.scale = scale
        self.scaleType = scaleType
        _scaleSettingsStore = StateObject(wrappedValue: ScaleSettingsStore(scale: scale))
    }
    let lang = ScaleSettingsStrings.self
    
    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: truncatedTitle,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: { EmptyView() },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )
            
            List {
                scaleImageSection()
                if scaleType == .bluetoothR4 && scaleSettingsStore.isDeviceConnected {
                    if !scaleSettingsStore.isWifiConfigured && scaleSettingsStore.connectedWifiSSID == nil {
                        setupWiFiItem()
                    }
                    if self.scaleSettingsStore.isWeighOnlyModeEnabledByOthers {
                        enableBodyMetricsItem()
                    }
                }
                settingsSection()
                if scaleType == .bluetoothR4 {
                    connectionSection()
                }
                supportSection()
                deleteScaleSection()
                
                // Other section should be enable for the R4 scales and if canEnableTestingFeatures flag is true
                if scaleType == .bluetoothR4  && AppConstants.canEnableTestingFeatures == true {
                    othersSection()
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
        }
        .inAppBrowser(
            url: scaleSettingsStore.productURL ?? URL(string: AppConstants.Product.baseURL)!,
            isPresented: $scaleSettingsStore.showProductBrowser
        )
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .sheet(isPresented: $isOtherSettingsSheetPresented) {
            AdditionalSettingsSheet(scale: scale)
        }
        .sheet(isPresented: $isSoftwareUpdatePresented) {
            SoftwareUpdateSheet(
                scale: scale,
                currentFirmware: scaleSettingsStore.firmwareVersion,
                latestVersion: scale.metaData?.latestVersion
            )
        }
        .navigationBarBackButtonHidden(true)
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
    
    private func setupWiFiItem() -> some View {
        scaleStatusSection {
            ScaleStatusBanner(type: .setupIncomplete {
                router.navigate(to: .wifi(scale: scale))
            })
        }
    }
    
    private func enableBodyMetricsItem() -> some View {
        scaleStatusSection {
            ScaleStatusBanner(type: .weightOnly {
                scaleSettingsStore.handleEnableBodyMetrics()
            })
        }
    }
    
    private func deleteScaleSection() -> some View {
        Section {
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.deleteScale,
                    chevronType: .none,
                    isDestructive: true,
                    onTap: {
                        scaleSettingsStore.handleScaleDelete(scaleId: scale.id) {
                            router.navigateBack()
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
                        value: scaleSettingsStore.isBodyMetrics ? "All Body metrics" : "Weight only",
                        onTap: {
                            router.navigate(to: .scaleModes(scale: scale, isWeighOnlyModeEnabledByOthers: scaleSettingsStore.isWeighOnlyModeEnabledByOthers))
                        }
                    )
                )
                ActionListItemView(
                    config: ActionListItemConfig(
                        title: lang.displayMetrics,
                        onTap: { router.navigate(to: .displayMetrics(scale: scale, isWeighOnlyModeEnabledByOthers: scaleSettingsStore.isWeighOnlyModeEnabledByOthers)) }
                    )
                )
                ActionListItemView(
                    config: ActionListItemConfig(
                        title: lang.users,
                        value: scaleSettingsStore.displayName,
                        chevronType: scaleSettingsStore.isFetchingUsersList ? .loading : .right,
                        isDisabled: !scaleSettingsStore.isDeviceConnected,
                        onTap: {
                            Task {
                                await scaleSettingsStore.ensureUsersList()
                                router.navigate(to: .users(scale: scale, usersList: scaleSettingsStore.usersList))
                            }
                        }
                    )
                )
            }
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.scaleName,
                    value: scale.nickname ?? scale.deviceName,
                    onTap: { router.navigate(to: .scaleNameScreen(scale: scale)) }
                )
            )
            
            if let userNumber = scale.userNumber, scaleType != .bluetoothR4 {
                ActionListItemView(config: ActionListItemConfig(title: lang.userNumber, value: lang.userNumberInfo(userNumber), chevronType: .none))
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
                    value: scaleSettingsStore.isDeviceConnected ? ScaleBluetoothStrings.connected : ScaleBluetoothStrings.notConnected,
                    onTap: { router.navigate(to: .scaleBluetoothScreen(scale: scale)) }
                )
            )
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.wifi,
                    value: scaleSettingsStore.connectedWifiSSID,
                    isDisabled: !scaleSettingsStore.isDeviceConnected,
                    onTap: { router.navigate(to: .wifi(scale: scale)) }
                )
            )
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.wifiMacAddress,
                    chevronType: scaleSettingsStore.isFetchingWifiMacAddress ? .loading : .right,
                    isDisabled: !scaleSettingsStore.isDeviceConnected,
                    onTap: {
                        Task {
                            if let mac = scaleSettingsStore.wifiMacAddress {
                                router.navigate(to: .wifiMacAddress(macAddress: mac))
                            } else {
                                await scaleSettingsStore.ensureWifiMacAddress()
                                if let mac = scaleSettingsStore.wifiMacAddress {
                                    router.navigate(to: .wifiMacAddress(macAddress: mac))
                                }
                            }
                        }
                    }
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
                    value: scaleType.displayName,
                    chevronType: .none,
                    onTap: {}
                )
            )
            
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.sku.uppercased(),
                    value: scale.sku,
                    chevronType: .none
                )
            )
            
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.datePaired,
                    value: DateTimeTools.getFormattedDate(scale.createdAt ?? ""),
                    chevronType: .none
                )
            )
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.productGuide,
                    onTap: { scaleSettingsStore.openProductGuide(for: scale.sku ?? "") }
                )
            )
        }
        .listRowInsets()
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }
    
    private func othersSection() -> some View {
        Section(header: SectionHeader(title: lang.othersSectionHeader)) {
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.scaleMac,
                    value: scale.mac,
                    chevronType: .none
                )
            )
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.softwareUpdate,
                    isDisabled: !(((scale.metaData?.latestVersion ?? "") != (scaleSettingsStore.firmwareVersion ?? ""))
                                   && scaleSettingsStore.isDeviceConnected
                                   && scaleSettingsStore.isWifiConfigured),
                    onTap: {
                        let canProceed = ((scale.metaData?.latestVersion ?? "") != (scaleSettingsStore.firmwareVersion ?? ""))
                            && scaleSettingsStore.isDeviceConnected
                            && scaleSettingsStore.isWifiConfigured
                        if canProceed {
                            isSoftwareUpdatePresented = true
                        }
                    }
                )
            )
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.otherSettings,
                    onTap: { isOtherSettingsSheetPresented = true }
                )
            )
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.sessionImpedance,
                    chevronType: .none,
                    toggleBinding: Binding(get: { scaleSettingsStore.isImpedanceSwitchedOnForSession }, set: { val in
                        scaleSettingsStore.isImpedanceSwitchedOnForSession = val
                    }),
                    isDisabled: !scaleSettingsStore.isDeviceConnected || scaleSettingsStore.isScaleImpedanceSwitchedOn == true || (scaleSettingsStore.scale.r4ScalePreference?.shouldMeasureImpedance == false),
                    onTap: {
                        Task { await scaleSettingsStore.setSessionImpedance(scaleSettingsStore.isImpedanceSwitchedOnForSession) }
                    }
                )
            )
        }
        .listRowInsets()
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }
    
    @ViewBuilder
    private func scaleStatusSection<Content: View>(_ content: @escaping () -> Content) -> some View {
        Section {
            content()
        }
        .listRowInsets()
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }
    
}
