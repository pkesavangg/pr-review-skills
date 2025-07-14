//
//  WifiNetworksListView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 14/07/25.
//
import SwiftUI

/// Helper view for the network list with dividers
struct WifiNetworksListView: View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    
    let networks: [WifiDetails]
    
    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            ForEach(networks, id: \.ssid) { network in
                networkListItem(network: network) {
                    // TODO: Need to show the password screen for the selected network
                }
                
                if network.ssid != networks.last?.ssid {
                    Divider()
                        .frame(height: 0.5)
                        .frame(maxWidth: .infinity)
                        .background(theme.statusUtilityPrimary)
                        .padding(.leading, .spacingXL)
                }
            }
        }
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusXS)
    }
    
    @ViewBuilder
    private func networkListItem(network: WifiDetails, onTap: @escaping () -> Void) -> some View {
        ListItemView(
            leadingImage: AppAssets.wifi,
            title: network.ssid ?? "Unknown Network",
            trailing: Image(AppAssets.chevronRight)
                .foregroundColor(theme.actionPrimary),
            rowHeight: 48,
            onTap: onTap,
            verticalPadding: .zero
        )
    }
}
