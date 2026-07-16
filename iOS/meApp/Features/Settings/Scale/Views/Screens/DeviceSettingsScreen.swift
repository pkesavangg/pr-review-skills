//
//  DeviceSettingsScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 24/06/25.
//

import SwiftUI

struct DeviceSettingsScreen: View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    @StateObject private var scaleSettingsStore: DeviceSettingsStore
    let scale: Device
    var scaleType: DeviceModelType
    @State private var isOtherSettingsSheetPresented = false
    @State private var isSoftwareUpdatePresented = false

    private static let titleTruncationLength = 25
    private var fallbackProductURL: URL { AppConstants.LegalURLs.greaterGoodsWebsite }
    private var truncatedTitle: String {
        let title = scaleSettingsStore.nickname ?? scale.nickname ?? scale.deviceName ?? ""
        return title.count > Self.titleTruncationLength ? "\(title.prefix(Self.titleTruncationLength))…" : title
    }

    init(scale: Device, scaleType: DeviceModelType) {
        self.scale = scale
        self.scaleType = scaleType
        _scaleSettingsStore = StateObject(wrappedValue: DeviceSettingsStore(scale: scale))
    }
    let lang = DeviceSettingsStrings.self

    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: truncatedTitle,
                leadingContent: { AppIconView(icon: AppAssets.chevronLeft) },
                trailingContent: { EmptyView() },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true,
                leadingAccessibilityID: AccessibilityID.deviceSettingsBackButton
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
                if scaleType == .bluetoothR4 || scaleType == .bluetoothA3 || scaleType == .bluetoothA6 {
                    connectionSection()
                }
                supportSection()
                deleteScaleSection()

                // Other section should be enable for the R4 scales and if canEnableTestingFeatures flag is true
                if scaleType == .bluetoothR4 && AppConstants.canEnableTestingFeatures == true {
                    othersSection()
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
        }
        .inAppBrowser(
            url: scaleSettingsStore.productURL ?? fallbackProductURL,
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
        .navigationBarHidden(true)
        .screenAccessibilityRoot(AccessibilityID.deviceSettingsScreenRoot)
        .onAppear {
            scaleSettingsStore.refreshScaleData()
        }
    }

    // MARK: - Sections as Functions
    private func scaleImageSection() -> some View {
        // Map SKU for display (e.g., 0022 -> 0383) for SCALES lookup
        let sku = scale.sku ?? ""
        let lookupSku = DeviceHelper.mapSkuForDisplay(sku)
        let imagePath = SCALES.first { $0.sku == lookupSku }?.imgPath
            ?? bpmCatalogItem(forEnteredCode: sku)?.imgPath
            ?? AppAssets.scale0412 // fallback
        return Image(imagePath)
            .resizable()
            .scaledToFit()
            .frame(width: 180, height: 180)
            .frame(maxWidth: .infinity)
            .listRowBackground(Color.clear)
            .themeDropShadow()
    }

    private func setupWiFiItem() -> some View {
        scaleStatusSection {
            DeviceStatusBanner(type: .setupIncomplete {
                router.navigate(to: .wifi(scale: scale))
            }, actionAccessibilityID: AccessibilityID.deviceSettingsSetupWifiButton)
        }
    }

    private func enableBodyMetricsItem() -> some View {
        scaleStatusSection {
            DeviceStatusBanner(type: .weightOnly {
                scaleSettingsStore.handleEnableBodyMetrics()
            }, actionAccessibilityID: AccessibilityID.deviceSettingsEnableBodyMetricsButton)
        }
    }

    private func deleteScaleSection() -> some View {
        Section {
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.deleteScale,
                    chevronType: .none,
                    isDestructive: true
                ) {
                        scaleSettingsStore.handleScaleDelete(scaleId: scale.id) {
                            router.navigateBack()
                        }
                    }
            )
            .appAccessibility(id: AccessibilityID.deviceSettingsDeleteButton)
        }
        .listRowInsets()
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }

    private func settingsSection() -> some View {
        Section(header: SectionHeader(title: lang.settingsSectionHeader)) {
            if scaleType == .bluetoothR4 {
                r4SettingsRows()
            }
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.scaleName,
                    value: scaleSettingsStore.nickname
                ) { router.navigate(to: .deviceNameScreen(scale: scale)) }
            )
            .appAccessibility(id: AccessibilityID.deviceSettingsScaleNameRow)

            if let userNumber = scale.userNumber, scaleType != .bluetoothR4 {
                ActionListItemView(config: ActionListItemConfig(title: lang.userNumber, value: lang.userNumberInfo(userNumber), chevronType: .none))
            }
        }
        .listRowInsets()
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }

    /// R4-only rows (Mode / Display Metrics / Users). Extracted so `settingsSection()`
    /// stays within the SwiftLint function-body length budget.
    @ViewBuilder
    private func r4SettingsRows() -> some View {
        ActionListItemView(
            config: ActionListItemConfig(
                title: lang.mode,
                value: scaleSettingsStore.isBodyMetrics ? "All Body metrics" : "Weight only"
            ) {
                router.navigate(to: .deviceModes(
                    scale: scale,
                    isWeighOnlyModeEnabledByOthers: scaleSettingsStore.isWeighOnlyModeEnabledByOthers
                ))
            }
        )
        .appAccessibility(id: AccessibilityID.deviceSettingsModeRow)
        ActionListItemView(
            config: ActionListItemConfig(
                title: lang.displayMetrics
            ) {
                router.navigate(to: .displayMetrics(
                    scale: scale,
                    isWeighOnlyModeEnabledByOthers: scaleSettingsStore.isWeighOnlyModeEnabledByOthers
                ))
            }
        )
        .appAccessibility(id: AccessibilityID.deviceSettingsDisplayMetricsRow)
        ActionListItemView(
            config: ActionListItemConfig(
                title: lang.users,
                value: scaleSettingsStore.displayName,
                chevronType: scaleSettingsStore.isFetchingUsersList ? .loading : .right,
                isDisabled: !scaleSettingsStore.isDeviceConnected
            ) {
                Task {
                    let fetchedUsersList = await scaleSettingsStore.ensureUsersList()
                    router.navigate(to: .users(scale: scale, usersList: fetchedUsersList))
                }
            }
        )
        .appAccessibility(id: AccessibilityID.deviceSettingsUsersRow)
    }

    private func connectionSection() -> some View {
        Section(header: SectionHeader(title: lang.connectionSectionHeader)) {
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.bluetooth,
                    value: scaleSettingsStore.isDeviceConnected ? DeviceBluetoothStrings.connected : DeviceBluetoothStrings.notConnected
                ) { router.navigate(to: .deviceBluetoothScreen(scale: scale)) }
            )
            .appAccessibility(id: AccessibilityID.deviceSettingsBluetoothRow)
            if scaleType == .bluetoothR4 {
                ActionListItemView(
                    config: ActionListItemConfig(
                        title: lang.wifi,
                        value: scaleSettingsStore.connectedWifiSSID,
                        isDisabled: !scaleSettingsStore.isDeviceConnected
                    ) { router.navigate(to: .wifi(scale: scale)) }
                )
                .appAccessibility(id: AccessibilityID.deviceSettingsWifiRow)
                ActionListItemView(
                    config: ActionListItemConfig(
                        title: lang.wifiMacAddress,
                        chevronType: scaleSettingsStore.isFetchingWifiMacAddress ? .loading : .right,
                        isDisabled: !scaleSettingsStore.isDeviceConnected
                    ) {
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
                .appAccessibility(id: AccessibilityID.deviceSettingsWifiMacRow)
            }
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
                    chevronType: .none
                ) {}
            )

            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.sku.uppercased(),
                    value: DeviceHelper.mapSkuForDisplay(scale.sku ?? ""),
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
                    title: lang.productGuide
                ) { scaleSettingsStore.openProductGuide(for: DeviceHelper.mapSkuForDisplay(scale.sku ?? "")) }
            )
            .appAccessibility(id: AccessibilityID.deviceSettingsProductGuideRow)
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
                                   && scaleSettingsStore.isWifiConfigured)
                ) {
                        let canProceed = ((scale.metaData?.latestVersion ?? "") != (scaleSettingsStore.firmwareVersion ?? ""))
                            && scaleSettingsStore.isDeviceConnected
                            && scaleSettingsStore.isWifiConfigured
                        if canProceed {
                            isSoftwareUpdatePresented = true
                        }
                    }
            )
            .appAccessibility(id: AccessibilityID.deviceSettingsSoftwareUpdateRow)
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.otherSettings
                ) { isOtherSettingsSheetPresented = true }
            )
            .appAccessibility(id: AccessibilityID.deviceSettingsOtherSettingsRow)
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.sessionImpedance,
                    chevronType: .none,
                    toggleBinding: Binding(get: { scaleSettingsStore.isImpedanceSwitchedOnForSession }, set: { val in
                        scaleSettingsStore.isImpedanceSwitchedOnForSession = val
                    }),
                    isDisabled: !scaleSettingsStore.isDeviceConnected ||
                        scaleSettingsStore.isScaleImpedanceSwitchedOn == true ||
                        !scaleSettingsStore.isBodyMetrics
                ) {
                        Task { await scaleSettingsStore.setSessionImpedance(scaleSettingsStore.isImpedanceSwitchedOnForSession) }
                    }
            )
            .appAccessibility(id: AccessibilityID.deviceSettingsSessionImpedanceToggle)
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
