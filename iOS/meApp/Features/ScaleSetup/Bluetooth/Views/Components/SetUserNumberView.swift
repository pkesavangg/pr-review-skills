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
                
                GifView(gifName: appAssets.setupSetUserNumberGifName(sku))
                    .frame(height: 211)
                    .frame(maxWidth: .infinity)
                
            }
            .padding(.top, .spacingLG)
        }
    }
}

#Preview {
    SetUserNumberView(sku: "0376", userNumber: 6)
}
