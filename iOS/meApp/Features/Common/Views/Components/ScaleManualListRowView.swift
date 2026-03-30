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
    let rowHeight: CGFloat = 139

    var body: some View {
        HStack(spacing: .spacingSM) {
            Image(scale.imgPath)
                .resizable()
                .scaledToFit()
                .frame(width: 75, height: 75)
                .themeDropShadow()

            VStack(alignment: .leading, spacing: 0) {
                Text(scale.sku)
                    .fontOpenSans(.heading5)
                    .foregroundColor(theme.textHeading)
                Text(scale.productName.lowercased())
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textSubheading)
                    .lineLimit(1)
                    .truncationMode(.tail)

            }
            .frame(height: 75)

            Spacer()
            AppIconView(icon: iconName(for: scale.setupType), size: IconSize(width: 32, height: 32))
                .foregroundColor(theme.actionPrimary)
            AppIconView(icon: AppAssets.chevronRight, size: IconSize(width: 32, height: 32))
                .foregroundColor(theme.actionPrimary)
        }
        .padding(.vertical, .spacingSM)
        .padding(.horizontal, .spacingSM)
        .frame(height: rowHeight)
        .border(sides: [.bottom], thickness: 0.5)
    }

    /// Returns human-readable connectivity label for a given setup type.
    private func connectivityText(for type: ScaleSetupType) -> String {
        switch type {
        case .bluetooth, .lcbt, .babyScale:
            return "Bluetooth"
        case .wifi, .espTouchWifi:
            return "WiFi"
        case .appSync:
            return "AppSync"
        case .btWifiR4:
            return "BtWifi"
        case .bpm:
            return "BPM"
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
        case .babyScale:
            return AppAssets.bluetooth
        case .bpm:
            return AppAssets.bluetooth
        }
    }
}

#Preview {
    ScaleManualListRowView(scale: SCALES[0])
}
