//
//  WifiScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 27/06/25.
//

import SwiftUI
import Combine

struct WifiScreen:View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    let lang = WifiScreenStrings.self
    @ObservedObject var scaleStore = ScaleStore()
    
    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: lang.title,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: { EmptyView() },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )
            
            if scaleStore.isWifiLoading{
                wifiLoaderView()
            }else{
                wifiListView()
            }
        }
        .frame(maxHeight: .infinity, alignment: .top)
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
    }
    
    private func wifiLoaderView() -> some View {
        VStack(alignment: .center, spacing: .spacingMD) {
            Text(lang.gatheringNetworks)
                .fontOpenSans(.heading4)
                .fontWeight(.bold)
                .foregroundColor(theme.textHeading)
            
            ConnectionIndicatorView(image: AppAssets.wifi, isFailure: false)
        }
        .frame(maxHeight: .infinity)
    }
    
    private func wifiListView() -> some View {
        // Use values from scaleStore
        let connectedWifiNetwork = scaleStore.connectedWifiNetwork
        let wifiNetworks: [String]
        if connectedWifiNetwork != nil {
            wifiNetworks = scaleStore.wifiNetworks.filter { $0 != connectedWifiNetwork }
        } else {
            wifiNetworks = scaleStore.wifiNetworks
        }
        
        return Group {
            if let connected = connectedWifiNetwork {
                VStack(alignment: .leading, spacing: .spacingMD) {
                    // Connected Network Section
                    Text(lang.continueNetworkPrompt)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                    
                    Text(lang.connectedNetwork)
                        .fontOpenSans(.heading5)
                        .fontWeight(.bold)
                        .foregroundColor(theme.textHeading)
                    
                    VStack(spacing: 0) {
                        ListItemView(
                            leadingImage: AppAssets.wifi,
                            title: connected,
                            trailing: Image(AppAssets.chevronRight)
                                .foregroundColor(theme.actionPrimary),
                            rowHeight: 48,
                            onTap: {
                                router.navigate(to: .wifiCredentials(wifiName: connectedWifiNetwork ?? ""))
                            },
                            verticalPadding: .zero
                        )
                    }
                    .background(theme.backgroundPrimary)
                    .cornerRadius(.radiusXS)
                    
                    // Available Networks Section
                    Text(lang.availableNetworks)
                        .fontOpenSans(.heading5)
                        .fontWeight(.bold)
                        .foregroundColor(theme.textHeading)
                    
                    wifiNetworksListView(networks: wifiNetworks)
                    
                    ButtonView(text: lang.refresh, type: .textPrimary, size: .large, isDisabled: false, action: { scaleStore.refreshWifiNetworks() })
                        .frame(maxWidth: .infinity, alignment: .center)
                    
                    Spacer()
                }
                .padding(.horizontal, .spacingSM)
                .padding(.top, .spacingLG)
                .frame(maxHeight: .infinity)
            } else {
                VStack(alignment: .center, spacing: .spacingMD) {
                    Text(lang.multipleNetworksInfo)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                    
                    wifiNetworksListView(networks: wifiNetworks)
                    
                    ButtonView(text: lang.refresh, type: .textPrimary, size: .large, isDisabled: false, action: { scaleStore.refreshWifiNetworks() })
                    
                    Spacer()
                }
                .padding(.horizontal, .spacingSM)
                .padding(.top, .spacingLG)
                .frame(maxHeight: .infinity)
            }
        }
    }
    
    /// Helper function for the network list with dividers
    @ViewBuilder
    private func wifiNetworksListView(networks: [String]) -> some View {
        VStack(alignment: .center, spacing: 0) {
            ForEach(networks, id: \.self) { network in
                ListItemView(
                    leadingImage: AppAssets.wifi,
                    title: network,
                    trailing: Image(AppAssets.chevronRight)
                        .foregroundColor(theme.actionPrimary),
                    rowHeight: 48,
                    onTap: {
                        router.navigate(to: .wifiCredentials(wifiName: network))
                    },
                    verticalPadding: .zero
                )
                if network != networks.last {
                    Divider()
                        .frame(height: 1)
                        .frame(maxWidth: .infinity)
                        .background(theme.statusUtilityPrimary)
                }
            }
        }
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusXS)
    }
}

#Preview {
    WifiScreen()
}

