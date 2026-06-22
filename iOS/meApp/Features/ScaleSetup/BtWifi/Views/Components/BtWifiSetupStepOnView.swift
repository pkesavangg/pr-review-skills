//
//  BtWifiSetupStepOnView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 15/07/25.
//

import SwiftUI

struct BtWifiSetupStepOnView: View {
    @Environment(\.appTheme) private var theme
    private let lang = BtWifiScaleSetupStrings.StepOnStrings.self
    
    var body: some View {
        VStack(spacing: .spacingLG) {
            VStack(spacing: .spacingXS) {
                Text(lang.title)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)

                Text(lang.subtitle)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textHeading)
            }
            .accessibilityElement(children: .combine)

            VStack(alignment: .center) {
                GifView(gifName: AppAssets.stepOnGif, height: 211)
                    .frame(width: 370, height: 211)
                    .clipShape(RoundedRectangle(cornerRadius: .radiusSM))
                    .accessibilityHidden(true)
            }
            .frame(maxWidth: .infinity, alignment: .center)

        }
        .padding(.bottom, .spacingLG)
    }
}

#Preview {
    BtWifiSetupStepOnView()
}
