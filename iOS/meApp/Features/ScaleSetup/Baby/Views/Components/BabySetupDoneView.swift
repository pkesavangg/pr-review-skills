//
//  BabySetupDoneView.swift
//  meApp
//

import SwiftUI

/// "You're Done!" — final closing screen shown after both the full baby-profile
/// path and the skip path complete. FINISH button in the shared footer handles exit.
struct BabySetupDoneView: View {
    @Environment(\.appTheme) private var theme
    private let lang = BabyScaleSetupStrings.Done.self

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: .spacingLG) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(lang.title)
                        .fontOpenSans(.heading4)
                        .fontWeight(.bold)
                        .foregroundStyle(theme.textHeading)

                    Text(lang.subtitle)
                        .fontOpenSans(.body2)
                        .foregroundStyle(theme.textBody)
                }

                GifView(gifName: AppAssets.checkmarkSuccessGif, width: 160, height: 160)
                    .frame(width: 160, height: 160)
                    .frame(maxWidth: .infinity)
                    .accessibilityHidden(true)
            }
            .padding(.top, .spacingLG)
        }
    }
}
