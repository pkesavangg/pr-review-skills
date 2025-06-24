//
//  ScaleManualListRowView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 23/06/25.
//

import SwiftUI

// MARK: - Scale Manual List Row View
struct ScaleManualListRowView: View {
    let scale: ScaleItemInfo
    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: .spacingSM) {
            // Image placeholder
            Rectangle()
                .fill(theme.statusUtility)
                .frame(width: 75, height: 75)
                .overlay(
                    // TODO: Replace with actual Scale image when available
                    Image(AppAssets.meLogoDark)
                        .resizable()
                        .scaledToFit()
                        .padding(12)
                )

            VStack(alignment: .leading) {
                Text(scale.sku)
                    .fontOpenSans(.heading5)
                    .foregroundColor(theme.textHeading)
                Text(scale.productName.lowercased())
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textSubheading)
                Spacer()
                HStack(spacing: .spacingXS) {
                    AppIconView(icon: iconName(for: scale.setupType), size: IconSize(width: 20, height: 20))
                        .foregroundColor(theme.statusIconSecondary)

                    Text(connectivityText(for: scale.setupType))
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                }
            }
            .frame(height: 75)
            Spacer()
            Image(AppAssets.chevronRight)
                .foregroundColor(theme.actionPrimary)
        }
        .padding(.vertical, .spacingSM)
        .padding(.horizontal, .spacingSM)
        .frame(height: 139)
        .border(sides: [.bottom], thickness: 0.5)
    }

    /// Returns human-readable connectivity label for a given setup type.
    private func connectivityText(for type: ScaleSetupType) -> String {
        switch type {
        case .bluetooth, .lcbt:
            return "Bluetooth"
        case .wifi, .espTouchWifi:
            return "WiFi"
        case .appSync:
            return "AppSync"
        case .btWifiR4:
            return "BtWifi"
        }
    }

    private func iconName(for type: ScaleSetupType) -> String {
        switch type {
        case .bluetooth, .lcbt:
            return AppAssets.bluetooth
        case .wifi, .espTouchWifi:
            return AppAssets.wifi
        case .appSync:
            return AppAssets.appSync
        case .btWifiR4:
            return AppAssets.btWifi
        }
    }
}

#Preview {
    ScaleManualListRowView(scale: SCALES[0])
}