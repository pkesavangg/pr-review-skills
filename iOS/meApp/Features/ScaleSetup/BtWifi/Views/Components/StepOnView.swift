//
//  StepOnView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 15/07/25.
//

import SwiftUI

struct StepOnView: View {
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
            GifView(gifName: AppAssets.stepOnGif)
                .frame(height: 211)
                .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: .radiusSM))
        }
        .padding(.bottom, .spacingLG)
    }
}

#Preview {
    StepOnView()
}
