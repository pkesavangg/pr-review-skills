//
//  SetUserNumberView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 19/07/25.
//

import SwiftUI

struct SetUserNumberView: View {
    @Environment(\.appTheme) private var theme
    let sku: String
    let userNumber: Int
    private let lang = BluetoothSetupViewStrings.SetUserViewStrings.self
    private let appAssets = AppAssets.self
    private let selTextSku = "0375"
    
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: .spacingLG) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(lang.title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .multilineTextAlignment(.leading)
                        .lineLimit(nil)
                    
                    Text(lang.description(sku == selTextSku, userNumber).asAttributed(withBoldWords: lang.boldWords))
                        .foregroundColor(theme.textBody)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, .spacingXSM)
                
                VStack(alignment: .center) {
                    GifView(gifName: appAssets.setupSetUserNumberGifName(sku), height: 250)
                        .frame(width: DevicePlatform.isMiniPhone ? 350 : 370,
                               height: DevicePlatform.isMiniPhone ? 200 : 250)
                        .scaleEffect(DevicePlatform.isMiniPhone ? 0.8 : 0.81)
                        .accessibilityHidden(true)
                }
                .frame(maxWidth: .infinity, alignment: .center)
                
            }
            .padding(.top, .spacingLG)
        }
    }
}

#Preview {
    SetUserNumberView(sku: "0376", userNumber: 6)
}
