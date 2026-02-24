//
//  ScaleBluetoothScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import GGBluetoothSwiftPackage
import SwiftUI

struct ScaleBluetoothScreen: View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var themeManager: Theme
    @ObservedObject var permissionsStore = PermissionsStore()
    let scale: Device
    let lang = ScaleBluetoothStrings.self

    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: lang.bluetooth,
                leadingContent: { AppIconView(icon: AppAssets.chevronLeft) },
                trailingContent: { EmptyView() },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )

            VStack(alignment: .leading, spacing: 0) {
                infoText

                divider()

                scaleItemView

                divider()

                sectionTitle(lang.permissionsTitle)
                    .padding(.top, .spacingMD)

                permissionItems
            }
        }
        .frame(maxHeight: .infinity, alignment: .top)
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
    }

    // MARK: - Subviews

    private var infoText: some View {
        Text(lang.info)
            .fontOpenSans(.body2)
            .foregroundColor(theme.textBody)
            .padding(.horizontal, .spacingSM)
            .padding(.top, .spacingLG)
            .padding(.bottom, .spacingMD)
    }

    private func scaleIcon(for sku: String?) -> Image {
        // Map SKU for display (e.g., 0022 -> 0383) for SCALES lookup
        let lookupSku = DeviceHelper.mapSkuForDisplay(sku ?? "")
        let imagePath = SCALES.first { $0.sku == lookupSku }?.imgPath ?? AppAssets.meLogoDark
        return Image(imagePath)
    }

    private var scaleItemView: some View {
        ScaleItemView(
            scaleIcon: scaleIcon(for: scale.sku),
            modelNumber: DeviceHelper.mapSkuForDisplay(scale.sku ?? "----"),
            scaleName: getScaleDisplayName(),
            status: (scale.isConnected ?? false) ? ScaleConnectionStatus.connected : ScaleConnectionStatus.notConnected,
            onTap: {},
            hideChevron: true,
            scaleType: ScaleTypeHelper.determineScaleType(for: scale)
        )
    }
    
    private func getScaleDisplayName() -> String {
        return scale.nickname ?? scale.deviceName ?? ""
    }
    
    private var permissionItems: some View {
        VStack(spacing: 0) {
            permissionRow(
                title: lang.bluetoothAuthorized,
                isEnabled: permissionsStore.isBluetoothAuthorized
            ) { permissionsStore.handleBluetoothAuthorizationTap() }
            divider()
            permissionRow(
                title: lang.bluetoothOn,
                isEnabled: permissionsStore.isBluetoothOn
            ) { permissionsStore.handleBluetoothSwitchTap() }
        }
        .background(theme.backgroundPrimary)
        .cornerRadius(.spacingXS)
        .padding(.horizontal, .spacingSM)
        .padding(.bottom, .spacingMD)
    }

    private func permissionRow(
        title: String,
        isEnabled: Bool,
        onTap: @escaping () -> Void
    ) -> some View {
        ListItemView(
            leadingImage: isEnabled ? AppAssets.filledTickCircle : AppAssets.minusCircleClear,
            useThemedImage: !isEnabled,
            leadingImageColor: isEnabled ? nil : theme.statusIconSecondary,
            title: title,
            trailing: isEnabled ? nil : Image(AppAssets.chevronRight)
                .foregroundColor(theme.actionPrimary),
            onTap: isEnabled ? nil : onTap,
            verticalPadding: .zero
        )
    }

    private func divider() -> some View {
        Divider()
            .foregroundColor(theme.statusUtilityPrimary)
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text)
            .fontOpenSans(.heading5)
            .fontWeight(.bold)
            .foregroundColor(theme.textHeading)
            .padding(.horizontal, .spacingSM)
            .padding(.bottom, .spacingSM)
    }
}

#if DEBUG
struct ScaleBluetoothScreen_Previews: PreviewProvider {
    static var previews: some View {
        let mockDevice = Device(
            id: "1",
            accountId: "demo-account",
            sku: SettingsConstants.defaultR4Sku,
            deviceName: "AccuCheck Verve Smart Scale"
        )
        ScaleBluetoothScreen(scale: mockDevice)
            .environmentObject(Router<SettingsRoute>())
    }
}
#endif
