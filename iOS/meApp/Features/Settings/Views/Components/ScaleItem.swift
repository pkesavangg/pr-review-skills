//
//  ScaleItem.swift
//  meApp
//
//  Created by Lakshmi Priya on 23/06/25.
//

import SwiftUI

struct ScaleItemView: View {
    @Environment(\.appTheme) private var theme
    let scaleIcon: Image
    let modelNumber: String
    let scaleName: String
    let status: ScaleConnectionStatus
    let onTap: () -> Void

    private var statusIconDetails: (icon: String, color: Color) {
        switch status {
        case .setupIncomplete:
            return (AppAssets.exclamationMark, theme.statusError)
        case .connected:
            return (AppAssets.bluetooth, theme.statusIconPrimary)
        case .notConnected:
            return (AppAssets.bluetooth, theme.statusIconSecondary)
        }
    }

    var body: some View {
        HStack(spacing: .spacingSM) {
            scaleIcon
                .resizable()
                .scaledToFit()
                .frame(width: 75, height: 75)

            VStack(alignment: .leading) {
                Text(modelNumber)
                    .fontOpenSans(.heading5)
                    .fontWeight(.bold)
                    .foregroundColor(theme.textHeading)

                Text(scaleName)
                    .fontOpenSans(.subHeading1)
                    .foregroundColor(theme.textSubheading)
                    .padding(.bottom, .spacingXS)

                HStack(spacing: .spacingXS) {
                    AppIconView(
                        icon: statusIconDetails.icon,
                        size: IconSize(width: 24, height: 24)
                    )
                    .foregroundColor(statusIconDetails.color)

                    Text(status.displayText)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                }
            }
            
            Spacer()

            Button(action: {
                onTap()
            }) {
                Image(AppAssets.chevronRight)
                    .foregroundColor(theme.actionPrimary)
                    .frame(width: 24, height: 24)
            }
           
        }
        .padding(.horizontal, .spacingSM)
        .padding(.vertical, .spacingLG)
        .contentShape(Rectangle())
    }
}

// MARK: - Preview

struct ScaleItemView_Previews: PreviewProvider {
    static var previews: some View {
        VStack(spacing: 0) {
            ScaleItemView(
                scaleIcon: Image(AppAssets.meLogoDark),
                modelNumber: "0412",
                scaleName: "accucheck verve smart scale",
                status: .connected,
                onTap: {}
            )
            Divider()
            ScaleItemView(
                scaleIcon: Image(AppAssets.meLogoDark),
                modelNumber: "0412",
                scaleName: "accucheck verve smart scale",
                status: .notConnected,
                onTap: {}
            )
            Divider()
            ScaleItemView(
                scaleIcon: Image(AppAssets.meLogoDark),
                modelNumber: "0412",
                scaleName: "accucheck verve smart scale",
                status: .setupIncomplete,
                onTap: {}
            )
            Divider()
        }
        .background(Color(.systemGray6))
    }
}
