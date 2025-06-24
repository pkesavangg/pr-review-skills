//
//  AddAndEditScalesScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 23/06/25.
//

import SwiftUI

struct MyScalesScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var router: Router<SettingsRoute>
    @StateObject var scaleStore = ScaleStore()
    let lang = MyScaleStrings.self

    var body: some View {
        VStack(alignment: .leading, spacing:0){
            NavbarHeaderView(
                title: lang.addEditScales,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: { EmptyView() },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )

            ScrollView (showsIndicators: false){
                VStack(alignment: .leading, spacing: .spacingXS){
                    Text(lang.addAScale)
                        .fontOpenSans(.heading4)
                        .fontWeight(.bold)
                        .foregroundColor(theme.textHeading)
                    Text(lang.enterModelNumber)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                }
                .padding(.horizontal, .spacingSM)
                .padding(.top, .spacingLG)
            }
        }
        .navigationBarBackButtonHidden(true)
        .background(theme.backgroundSecondary.ignoresSafeArea())
    }
}

#Preview {
    MyScalesScreen()
}
