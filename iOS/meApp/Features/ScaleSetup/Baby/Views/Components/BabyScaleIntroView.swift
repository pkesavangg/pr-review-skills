//
//  BabyScaleIntroView.swift
//  meApp
//

import SwiftUI

/// Intro screen showing baby scale model info and image.
struct BabyScaleIntroView: View {
    @Environment(\.appTheme) private var theme
    let scale: DeviceItemInfo
    private let lang = BabyScaleSetupStrings.self

    var body: some View {
        ScrollView {
            VStack(spacing: .spacingLG) {
                VStack(spacing: .spacingXS) {
                    Image(scale.imgPath)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 180, height: 180)
                        .cornerRadius(.radiusLG)
                        .themeDropShadow()
                        .padding(.bottom, .spacingLG)
                        .accessibilityHidden(true)

                    Text(ScaleSetupStrings.modelTitle(scale.sku))
                        .fontOpenSans(.heading4)
                        .fontWeight(.bold)
                        .foregroundColor(theme.textHeading)

                    Text(lang.Intro.smartBabyScale)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                }
                .accessibilityElement(children: .combine)
                .frame(maxWidth: .infinity)

                Text(lang.Intro.troubleSettingUp)
                    .fontOpenSans(.body2)
                    .multilineTextAlignment(.leading)
                    .foregroundColor(theme.textBody)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.top, .spacingLG)
        }
    }
}
