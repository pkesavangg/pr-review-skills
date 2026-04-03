//
//  BpmDeviceSettingsScreen.swift
//  meApp
//

import SwiftUI

struct BpmDeviceSettingsScreen: View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    @StateObject private var bpmStore: BpmDeviceSettingsStore
    let device: Device

    private static let titleTruncationLength = 25
    private var fallbackProductURL: URL { AppConstants.LegalURLs.greaterGoodsWebsite }
    private var truncatedTitle: String {
        let title = device.nickname ?? device.deviceName ?? ""
        return title.count > Self.titleTruncationLength ? "\(title.prefix(Self.titleTruncationLength))…" : title
    }

    init(device: Device) {
        self.device = device
        _bpmStore = StateObject(wrappedValue: BpmDeviceSettingsStore(device: device))
    }
    let lang = BpmDeviceSettingsStrings.self

    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: truncatedTitle,
                leadingContent: { AppIconView(icon: AppAssets.chevronLeft) },
                trailingContent: { EmptyView() },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )

            List {
                deviceImageSection()
                settingsSection()
                connectionSection()
                supportSection()
                deleteDeviceSection()
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
        }
        .inAppBrowser(
            url: bpmStore.productURL ?? fallbackProductURL,
            isPresented: $bpmStore.showProductBrowser
        )
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
        .onAppear {
            bpmStore.refreshDeviceData()
        }
    }

    // MARK: - Sections

    private func deviceImageSection() -> some View {
        let sku = device.sku ?? ""
        let imagePath = bpmCatalogItem(forEnteredCode: sku)?.imgPath ?? AppAssets.bpm0604
        return Image(imagePath)
            .resizable()
            .scaledToFit()
            .frame(width: 180, height: 180)
            .frame(maxWidth: .infinity)
            .listRowBackground(Color.clear)
            .themeDropShadow()
    }

    private func settingsSection() -> some View {
        Section(header: SectionHeader(title: lang.settingsSectionHeader)) {
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.deviceName,
                    value: device.nickname ?? device.deviceName
                ) { router.navigate(to: .scaleNameScreen(scale: device)) }
            )

            if let userNumber = device.userNumber {
                ActionListItemView(
                    config: ActionListItemConfig(
                        title: lang.userNumber,
                        value: lang.userNumberInfo(userNumber),
                        chevronType: .none
                    )
                )
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
                    value: bpmStore.isDeviceConnected ? ScaleBluetoothStrings.connected : ScaleBluetoothStrings.notConnected
                ) { router.navigate(to: .scaleBluetoothScreen(scale: device)) }
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
                    title: lang.deviceType,
                    value: ScaleType.bpm.displayName,
                    chevronType: .none
                ) {}
            )

            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.sku.uppercased(),
                    value: device.sku ?? "",
                    chevronType: .none
                )
            )

            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.datePaired,
                    value: DateTimeTools.getFormattedDate(device.createdAt ?? ""),
                    chevronType: .none
                )
            )

            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.productGuide
                ) { bpmStore.openProductGuide(for: device.sku ?? "") }
            )
        }
        .listRowInsets()
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }

    private func deleteDeviceSection() -> some View {
        Section {
            ActionListItemView(
                config: ActionListItemConfig(
                    title: lang.deleteDevice,
                    chevronType: .none,
                    isDestructive: true
                ) {
                    bpmStore.handleDeviceDelete(deviceId: device.id) {
                        router.navigateBack()
                    }
                }
            )
        }
        .listRowInsets()
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }
}
