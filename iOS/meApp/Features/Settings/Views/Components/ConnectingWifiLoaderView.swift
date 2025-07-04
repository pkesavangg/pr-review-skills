//
//  ConnectingWifiLoaderView.swift
//  meApp
//
//  Created by Lakshmi Priya on 30/06/25.
//

import SwiftUI

struct ConnectingWifiLoaderView: View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    let lang = WifiScreenStrings.self
    @ObservedObject var store: ScaleStore
    
    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: lang.title,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: { EmptyView() },
                onLeadingTap: { router.navigateToRoot() },
                onTrailingTap: {},
                canShowBorder: true
            )
            
            Spacer()
            
            VStack(alignment:.center, spacing: .spacingMD){
                Text(lang.connectingToWifi)
                    .fontOpenSans(.heading4)
                    .fontWeight(.bold)
                    .foregroundColor(theme.textHeading)
                
                Image(AppAssets.scale0412)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 180, height: 180)
                
                SetupLoaderView(connectionState: store.wifiConnectionState)
                
                ConnectionIndicatorView(
                    image: AppAssets.wifi,
                    isFailure: store.wifiConnectionState == .failure,
                    showPulsingCircle: store.wifiConnectionState != .success
                )
                
            }
            
            Spacer()
        }
        .frame(maxHeight: .infinity, alignment: .top)
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
    }
}
