//
//  DeviceManualListRowView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 23/06/25.
//

import SwiftUI

// MARK: - Scale Manual List Row View
struct DeviceManualListRowView: View {
    let scale: DeviceItemInfo
    var showConnectivityIcon: Bool = true
    var showBottomBorder: Bool = true
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
                Text(scale.setupType == .bpm ? bpmListModelLabel(primarySku: scale.sku) : scale.sku)
                    .fontOpenSans(.heading5)
                    .foregroundColor(theme.textHeading)
                Text(scale.productName.lowercased())
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textSubheading)
                    .fixedSize(horizontal: false, vertical: true)

    }
    .frame(height: 75)

            Spacer()
            if showConnectivityIcon {
                AppIconView(icon: iconName(for: scale.setupType), size: IconSize(width: 32, height: 32))
                    .foregroundColor(theme.actionPrimary)
            }
            AppIconView(icon: AppAssets.chevronRight, size: IconSize(width: 32, height: 32))
                .foregroundColor(theme.actionPrimary)
        }
        .padding(.vertical, .spacingSM)
        .padding(.horizontal, .spacingSM)
        .frame(height: rowHeight)
        .border(sides: [.bottom], thickness: showBottomBorder ? 0.5 : 0)
    }

    /// Returns human-readable connectivity label for a given setup type.
    private func connectivityText(for type: DeviceSetupType) -> String {
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

    private func iconName(for type: DeviceSetupType) -> String {
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
            return AppAssets.babyAppIcon
        case .bpm:
            return AppAssets.bpmIcon
        }
    }
}

#Preview {
    DeviceManualListRowView(scale: SCALES[0])
}
