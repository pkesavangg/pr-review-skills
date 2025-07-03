//
//  ScaleBluetoothScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

struct ScaleBluetoothScreen: View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
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

    private var scaleItemView: some View {
        ScaleItemView(
            scaleIcon: Image(AppAssets.meLogoDark),
            modelNumber: scale.sku ?? "----",
            scaleName: scale.deviceName ?? lang.unknownScale,
            status: .connected, // TODO: Replace with actual status if available
            onTap: {},
            hideChevron: true
        )
    }

    private var permissionItems: some View {
        VStack(spacing: 0) {
            permissionRow(title: lang.bluetoothAuthorized)
            divider()
            permissionRow(title: lang.bluetoothOn)
        }
        .background(theme.backgroundPrimary)
        .cornerRadius(.spacingXS)
        .padding(.horizontal, .spacingSM)
        .padding(.bottom, .spacingMD)
    }

    private func permissionRow(title: String) -> some View {
        ListItemView<EmptyView>(
            leadingImage: AppAssets.filledTickCircle,
            title: title,
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
