//
//  WifiNetworksListView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 14/07/25.
//
import SwiftUI

/// Helper view for the network list with dividers
struct WifiNetworksListView: View {
    @Environment(\.appTheme) private var theme
    
    let networks: [WifiDetails]
    let onNetworkSelected: (WifiDetails) -> Void
    
    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            ForEach(networks, id: \.id) { network in
                Button(action: {
                    onNetworkSelected(network)
                }) {
                    networkListItem(network: network) {
                        onNetworkSelected(network)
                    }
                }
                if network.id != networks.last?.id {
                    Divider()
                        .frame(height: 0.5)
                        .frame(maxWidth: .infinity)
                        .background(theme.statusUtilityPrimary)
                        .padding(.leading, .spacingXL)
                }
            }
        }
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusSM)
    }
    
    @ViewBuilder
    private func networkListItem(network: WifiDetails, onNetworkSelected: @escaping () -> Void) -> some View {
        ListItemView(
            leadingImage: AppAssets.wifi,
            title: network.ssid ?? "Unknown Network",
            trailing: Image(AppAssets.chevronRight)
                .foregroundColor(theme.actionPrimary),
            rowHeight: 48,
            onTap: onNetworkSelected,
            verticalPadding: .zero
        )
    }
}
