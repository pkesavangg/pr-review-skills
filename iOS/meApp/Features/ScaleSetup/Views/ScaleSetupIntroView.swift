//
//  ScaleSetupIntroView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 01/07/25.
//
import SwiftUI

struct ScaleSetupIntroView: View {
    @Environment(\.appTheme) private var theme

    /// Scale metadata retrieved from the central `SCALES` array.
    let scale: ScaleItemInfo
    var onClick: (() -> Void)? = nil
    let scaleSetupLang = ScaleSetupStrings.self

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack {
                    VStack(spacing: .spacingLG) {
                        VStack(spacing: .spacingXS) {
                            Image(scale.imgPath)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 180, height: 180)
                                .cornerRadius(.radiusLG)
                                .themeDropShadow()
                                .padding(.bottom, .spacingLG)

                            Text(scaleSetupLang.modelTitle(scale.sku))
                                .fontOpenSans(.heading4)
                                .foregroundColor(theme.textHeading)

                            Text(scale.productName)
                                .fontOpenSans(.body2)
                                .foregroundColor(theme.textBody)
                        }
                        .padding(.horizontal, .spacingLG)

                        Text(scaleSetupLang.troubleSettingUp)
                            .fontOpenSans(.body2)
                            .multilineTextAlignment(.leading)
                            .foregroundColor(theme.textBody)

                        if let buttonTitle = self.buttonTitle {
                            ButtonView(text: buttonTitle, type: .inlineTextPrimary, size: .large, isDisabled: false) {
                                onClick?()
                            }
                        }
                    }
                    .frame(minHeight: geometry.size.height)
                    .frame(maxWidth: .infinity, alignment: .center)
                }
            }
        }
    }
    
    private var buttonTitle: String? {
        return  [.espTouchWifi, .wifi].contains(scale.setupType)
        ? scaleSetupLang.getScaleMacAddress
        : nil
    }
}


#Preview(body: {
    ScaleSetupIntroView(scale: SCALES[0]) {
        print("Button clicked")
    }
    .padding(.horizontal)
})
