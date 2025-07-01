//
//  ScaleInfoStepView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 01/07/25.
//
import SwiftUI

struct ScaleSetupInfoView: View {
    @Environment(\.appTheme) private var theme

    /// The SKU that identifies the scale model (e.g., "0397").
    let sku: String
    var onClick: (() -> Void)? = nil
    /// Lazy lookup for all copy & assets needed for this SKU.
    private var content: ScaleSetupInfoContent { ScaleSetupStrings.info(for: sku) }

    var body: some View {
        VStack(spacing: .spacingLG) {
            VStack(spacing: .spacingXS) {
                Image(content.imageName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 180, height: 180)
                    .cornerRadius(.radiusLG)
                    .dropShadow(DropShadow.glowBlack)
                    .padding(.bottom, .spacingLG)
                
                Text(content.title)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                
                Text(content.scaleName)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)
                
            }
            .padding(.horizontal, .spacingLG)
            
            Text(content.description)
                .fontOpenSans(.body2)
                .multilineTextAlignment(.leading)
                .foregroundColor(theme.textBody)

            if let buttonTitle = content.buttonTitle {
                ButtonView(text: buttonTitle, type: .inlineTextPrimary, size: .large, isDisabled: false) {
                    onClick?()
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .center)
    }
}


#Preview(body: {
    ScaleSetupInfoView(sku: "0343") {
        print("Button clicked")
    }
    .padding(.horizontal)
})
