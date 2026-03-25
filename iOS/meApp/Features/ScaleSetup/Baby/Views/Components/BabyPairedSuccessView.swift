//
//  BabyPairedSuccessView.swift
//  meApp
//

import SwiftUI

/// "You're Paired!" — success screen prompting baby profile creation.
struct BabyPairedSuccessView: View {
    @EnvironmentObject var store: BabyScaleSetupStore
    @Environment(\.appTheme) private var theme
    private let lang = BabyScaleSetupStrings.Paired.self

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: .spacingLG) {
                // Title + subtitle
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(lang.title)
                        .fontOpenSans(.heading3)
                        .fontWeight(.bold)
                        .foregroundColor(theme.textHeading)

                    Text(lang.subtitle)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                }

                // Animated success checkmark — centered
                GifView(gifName: AppAssets.checkmarkSuccessGif, width: 160, height: 160)
                    .frame(width: 160, height: 160)
                    .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, .spacingSM)
            .padding(.top, .spacingLG)
        }
    }
}
