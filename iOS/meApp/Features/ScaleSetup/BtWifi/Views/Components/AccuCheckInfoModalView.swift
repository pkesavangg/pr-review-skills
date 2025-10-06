//
//  AccuCheckInfoModalView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 16/07/25.
//
import SwiftUI

struct AccuCheckInfoModalView: View {
    @Environment(\.appTheme) private var theme
    let onClose: () -> Void
    let appAssets = AppAssets.self
    let lang = BtWifiScaleSetupStrings.AccuCheckInfoModalViewStrings.self
    
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                Button(action: onClose) {
                    AppIconView(icon: appAssets.xmarkSmall, size: IconSize(width: 24, height: 24))
                        .foregroundColor(theme.statusIconPrimary)
                }
            }
            .padding(.bottom, .spacingXS)
            
            VStack(spacing: .spacingSM) {
                Image(appAssets.accuCheckTickLarge)
                    .resizable()
                    .frame(width: 100, height: 100)
                
                Text(lang.title)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                
                VStack(alignment: .leading, spacing: .spacingSM) {
                    Text(lang.description1)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                    
                    Text(lang.description2)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                }
            }
        }
        .padding(.spacingMD)
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusXL)
    }
}
