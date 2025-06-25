//
//  ScaleSettingsScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 24/06/25.
//

import SwiftUI

struct ScaleSettingsScreen: View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    let scaleName: String
    var scaleType: ScaleType
    
    var body: some View {
        VStack(alignment:.center, spacing:0){
            NavbarHeaderView(
                title: scaleName,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: { EmptyView() },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )
            
            VStack(){
                Image(AppAssets.scale0412)
                    .frame(width: 370)
                    .padding(.top, .spacing3XL)
                    .padding(.bottom, .spacingLG)
                
                if scaleType == .bluetoothR4 {
                    Section {
                        ScaleStatusBanner(type: .weightOnly {}) // TODO: Add action to define ScaleStatusBanner according to scale status
                        .settingsRowInsets()
                    }
                    .listRowBackground(theme.backgroundPrimary)
                    .listRowSeparatorTint(theme.statusUtility)
                }
                
                Spacer()
                
            }
            .padding(.horizontal, .spacingSM)
            
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
    }
}
