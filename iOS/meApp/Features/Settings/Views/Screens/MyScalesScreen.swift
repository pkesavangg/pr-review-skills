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
        VStack{
            NavbarHeaderView(
                title: lang.addEditScales,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: { EmptyView() },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )
            Spacer()
            Text("Hello, World!")
            Spacer()
        }
        .navigationBarBackButtonHidden(true)
        .background(theme.backgroundSecondary
            .ignoresSafeArea()
        )
    }
}
