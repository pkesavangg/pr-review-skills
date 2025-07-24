//
//  ActivatePairingModeView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 22/07/25.
//

import SwiftUI

struct ActivatePairingModeView: View {
    @Environment(\.appTheme) private var theme
    let sku: String
    private let lang = WifiScaleSetupStrings.ActivatePairingModeViewStrings.self
    private let appAssets = AppAssets.self
    
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: .spacingLG) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(lang.title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .multilineTextAlignment(.leading)
                        .lineLimit(nil)
                    
                    Text(lang.description.asAttributed(withBoldWords: lang.boldWords))
                        .foregroundColor(theme.textBody)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                
                GifView(gifName: appAssets.wifiStepOnGif(sku))
                    .frame(height: 260)
                    .frame(maxWidth: .infinity)
            }
            .padding(.top, .spacingLG)
        }
    }
}

#Preview {
    ActivatePairingModeView(sku: "0384")
}
