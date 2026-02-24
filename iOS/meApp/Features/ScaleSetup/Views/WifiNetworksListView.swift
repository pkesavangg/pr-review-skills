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
    let isInteractive: Bool
    let showChevron: Bool
    
    init(
        networks: [WifiDetails],
        onNetworkSelected: @escaping (WifiDetails) -> Void,
        isInteractive: Bool = true,
        showChevron: Bool = true
    ) {
        self.networks = networks
        self.onNetworkSelected = onNetworkSelected
        self.isInteractive = isInteractive
        self.showChevron = showChevron
    }
    
    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            ForEach(networks, id: \.id) { network in
                Group {
                    if isInteractive {
                        Button(action: {
                            onNetworkSelected(network)
// swiftlint:disable:next multiple_closures_with_trailing_closure
                        }) {
                            networkListItem(network: network, showChevron: showChevron) {
                                onNetworkSelected(network)
                            }
                        }
                    } else {
                        networkListItem(network: network, showChevron: showChevron) {
                            // Non-interactive row – no action
                        }
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
    private func networkListItem(network: WifiDetails, showChevron: Bool, onNetworkSelected: @escaping () -> Void) -> some View {
        ListItemView(
            leadingImage: AppAssets.wifi,
            title: network.ssid ?? "Unknown Network",
            trailing: Group { if showChevron { Image(AppAssets.chevronRight).foregroundColor(theme.actionPrimary) } },
            rowHeight: 48,
            onTap: onNetworkSelected,
            verticalPadding: .zero
        )
    }
}
