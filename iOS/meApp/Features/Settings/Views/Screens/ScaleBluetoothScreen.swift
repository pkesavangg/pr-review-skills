//
//  ScaleBluetoothScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI
import GGBluetoothSwiftPackage

struct ScaleBluetoothScreen: View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var themeManager: Theme
    @ObservedObject var scaleStore = ScaleStore()
    @ObservedObject var permissionsStore = PermissionsStore()
    let scale: Device
    let lang = ScaleBluetoothStrings.self

    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: lang.bluetooth,
                leadingContent: { Image(AppAssets.chevronLeft) },
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
        .onAppear {
            Task {
                await scaleStore.loadScale(scale)
            }
        }
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
        let imagePath = SCALES.first(where: { $0.sku == (sku ?? "") })?.imgPath ?? AppAssets.meLogoDark
        return Image(imagePath)
    }

    private var scaleItemView: some View {
        ScaleItemView(
            scaleIcon: scaleIcon(for: scale.sku),
            modelNumber: scale.sku ?? "----",
            scaleName: getScaleDisplayName(),
            status: scaleStore.determineConnectionStatus(for: scale),
            onTap: {},
            hideChevron: true
        )
    }
    
    private func getScaleDisplayName() -> String {
         
        if let deviceName = scale.deviceName, !deviceName.isEmpty {
            return deviceName
        }
        return "unknown scale"
    }
    
    private var permissionItems: some View {
        VStack(spacing: 0) {
            permissionRow(
                title: lang.bluetoothAuthorized,
                isEnabled: permissionsStore.isBluetoothAuthorized,
                onTap: { permissionsStore.handleBluetoothAuthorizationTap() }
            )
            divider()
            permissionRow(
                title: lang.bluetoothOn,
                isEnabled: permissionsStore.isBluetoothOn,
                onTap: { permissionsStore.handleBluetoothSwitchTap() }
            )
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
            leadingImage: isEnabled ? AppAssets.filledTickCircle : AppAssets.minusCircle,
            useThemedImage: !isEnabled,
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
            sku: "0412",
            deviceName: "AccuCheck Verve Smart Scale"
        )
        ScaleBluetoothScreen(scale: mockDevice)
            .environmentObject(Router<SettingsRoute>())
    }
}
#endif
