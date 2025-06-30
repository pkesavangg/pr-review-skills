//
//  ScaleNameScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

struct ScaleNameScreen : View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    @ObservedObject var scaleStore = ScaleStore()
    let scaleName: String
    let lang = ScaleSettingsStrings.self
    let commonLang = CommonStrings.self
    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: lang.scaleName,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: {
                    AnyView(ButtonView(
                        text: commonLang.save.uppercased(),
                        type: .inlineTextPrimary,
                        size: .small,
                        isDisabled: false,
                        action: {
                            // TODO: ADD Action
                        }
                    ))
                },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )
            
            VStack{
                ListItemView(title: scaleName, subtitleTop: lang.scaleName.lowercased(), trailing: Image(AppAssets.closeCircle), rowHeight: 56, onTap: {})
                    .cornerRadius(.radiusXS)
                    .padding(.top, .spacingMD)
            }
            .padding(.horizontal, .spacingSM)
        }
        .frame(maxHeight: .infinity, alignment: .top)
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
    }
}

#Preview{
    ScaleNameScreen(scaleName: "AccuCheck Verve Smart Scale")
}
