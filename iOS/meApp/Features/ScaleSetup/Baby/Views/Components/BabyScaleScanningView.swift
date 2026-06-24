//
//  BabyScaleScanningView.swift
//  meApp
//

import SwiftUI

/// "Turn on your Scale" — shows the BabyAppLoader GIF animation while scanning for the scale.
struct BabyScaleScanningView: View {
    @Environment(\.appTheme) private var theme
    private let lang = BabyScaleSetupStrings.Wakeup.self

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: .spacingMD) {
                    VStack(spacing: .spacingXS) {
                        Text(lang.title)
                            .fontOpenSans(.heading4)
                            .foregroundColor(theme.textHeading)
                            .multilineTextAlignment(.center)

                        Text(lang.searching)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                            .multilineTextAlignment(.center)
                    }

                    GifView(gifName: AppAssets.babyAppLoaderGif, width: 200, height: 200)
                        .frame(width: 200, height: 200)
                }
                .frame(minHeight: geometry.size.height)
                .frame(maxWidth: .infinity, alignment: .center)
            }
        }
    }
}
