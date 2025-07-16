//
//  BtWiFiFinishStepView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 15/07/25.
//

import SwiftUI

struct BtWiFiFinishStepView: View {
    @Environment(\.appTheme) private var theme
    private let lang = BtWifiScaleSetupStrings.ScaleSetupFinishStrings.self
    let onWhatThisTapped: () -> Void
    var body: some View {
        VStack(spacing: .spacingLG) {
            VStack(spacing: .spacingXS) {
                Text(lang.title)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                
                Text(lang.subtitle)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textHeading)
                    .multilineTextAlignment(.center)
            }
            VStack {
                Image(AppAssets.accuCheck)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(height: 190)
                    .frame(maxWidth: .infinity)
                
                ButtonView(text: lang.whatThis, type: .inlineTextPrimary, size: .large, isDisabled: false) {
                    onWhatThisTapped()
                }
            }
        }
        .padding(.bottom, .spacingLG)
    }
}

#Preview {
    BtWiFiFinishStepView() {}
}
