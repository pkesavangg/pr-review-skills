//
//  WifiScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 27/06/25.
//

import SwiftUI

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
        // Example data
        let connectedWifiNetwork: String? = nil  // "greatergoods1"
        // TODO: Replace with real connection state
        let wifiNetworks: [String]
        if connectedWifiNetwork != nil {
            wifiNetworks = ["great2542", "ggtesting"] // Exclude the connected network if it's in the list, if needed
        } else {
            wifiNetworks = ["greatergoods1", "great2542", "ggtesting"]
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
                                router.navigate(to: .wifiCredentials)
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
                        router.navigate(to: .wifiCredentials)
                    },
                    verticalPadding: .zero
                )
                if network != networks.last {
                    Divider()
                        .frame(height: 1)
                        .frame(maxWidth: .infinity)
                        .background(theme.statusUtility)
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


struct WifiScreenStrings{
    static let title = "Wi-Fi Setup"
    static let gatheringNetworks = "Gathering Networks"
    static let refresh = "Refresh"
    static let multipleNetworksInfo = "If you have multiple Wi-Fi networks, pick the 2.4 GHz network closest to your scale."
    static let  continueNetworkPrompt = "Continue or choose a different 2.4 GHz Wi-Fi network."
    static let connectedNetwork = "Connected Network"
    static let availableNetworks = "Available Networks"
}


struct WifiCredentialsScreen: View {
    var body: some View {
        Text("Hello, World!")
    }
}

#Preview{
    WifiCredentialsScreen()
}
