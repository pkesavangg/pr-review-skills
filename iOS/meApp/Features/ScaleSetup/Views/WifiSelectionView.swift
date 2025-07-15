//
//  WifiSelectionView.swift
//  meApp
//
//  Created by Assistant on 27/06/25.
//

import SwiftUI

struct WifiSelectionView: View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    
    let connectedWifiNetwork: WifiDetails?
    let wifiNetworks: [WifiDetails]
    let onRefresh: () -> Void
    let onNetworkSelected: (WifiDetails) -> Void
    
    private let lang = BtWifiScaleSetupStrings.WifiScreenStrings.self
    private let itemHeight = 48
    
    var availableNetworks: [WifiDetails] {
        wifiNetworks.filter { network in
            guard let connectedSSID = connectedWifiNetwork?.ssid else { return true }
            return network.ssid != connectedSSID
        }
    }
    
    var availableNetworksHeight: CGFloat {
        CGFloat(min(itemHeight * availableNetworks.count, itemHeight * (connectedWifiNetwork != nil ? 5 : 7)))
    }
    
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 0) {
                    // Fixed header
                    wifiHeaderView(isConnected: connectedWifiNetwork != nil)
                        .padding(.top, .spacingMD)
                    
                    VStack(alignment: .leading, spacing: .spacingMD) {
                        // Fixed connected network section
                        if let connected = connectedWifiNetwork {
                            connectedNetworkSection(network: connected)
                        }
                        
                        // Available networks section with scrollable list
                        VStack(alignment: .leading, spacing: .spacingSM) {
                            if connectedWifiNetwork != nil {
                                Text(lang.availableNetworks)
                                    .fontOpenSans(.heading5)
                                    .fontWeight(.bold)
                                    .foregroundColor(theme.textHeading)
                            }
                            
                            // Scrollable available networks list
                            ScrollView(.vertical, showsIndicators: true) {
                                WifiNetworksListView(networks: availableNetworks, onNetworkSelected: onNetworkSelected)
                            }
                            .scrollDisabled(availableNetworks.count <= 5)
                            .frame(height: availableNetworksHeight)
                            .frame(maxWidth: .infinity)
                            .cornerRadius(.radiusSM)
                        }
                        
                        // Fixed refresh button
                        refreshButton()
                    }
                    .padding(.top, .spacingMD)
                    
                    Spacer()
                }
            }
        }
        .frame(maxHeight: .infinity)
        .background(theme.backgroundSecondary)
    }
    
    // MARK: - ViewBuilder Functions
    
    @ViewBuilder
    private func wifiHeaderView(isConnected: Bool) -> some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            Text(isConnected ? lang.alreadyConnected : lang.selectNetwork)
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)
            
            Text(isConnected ? lang.continueOrChooseDiff : lang.pickClosestNetwork)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
        }
    }
    
    @ViewBuilder
    private func connectedNetworkSection(network: WifiDetails) -> some View {
        VStack(alignment: .leading, spacing: .spacingSM) {
            Text(lang.connectedNetwork)
                .fontOpenSans(.heading5)
                .fontWeight(.bold)
                .foregroundColor(theme.textHeading)
            
            WifiNetworksListView(networks: [network], onNetworkSelected: onNetworkSelected)
        }
    }
    

    
    @ViewBuilder
    private func refreshButton() -> some View {
        ButtonView(
            text: lang.refresh,
            type: .textPrimary,
            size: .large,
            isDisabled: false,
            action: onRefresh
        )
        .frame(maxWidth: .infinity, alignment: .center)
    }
}

#Preview {
    WifiSelectionView(
        connectedWifiNetwork: WifiDetails(macAddress: "aa:bb:cc:dd:ee:ff", ssid: "Home WiFi"),
        wifiNetworks: [
            WifiDetails(macAddress: "aa:bb:cc:dd:ee:ff", ssid: "Home WiFi"),
            WifiDetails(macAddress: "11:22:33:44:55:66", ssid: "Office WiFi"),
            WifiDetails(macAddress: "aa:bb:cc:dd:ee:00", ssid: "Guest Network")
        ],
        onRefresh: {},
        onNetworkSelected: { _ in }
    )
}
