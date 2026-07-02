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
    let scale: DeviceItemInfo
    var troubleText: String = ScaleSetupStrings.troubleSettingUp
    var onClick: (() -> Void)?
    let scaleSetupLang = ScaleSetupStrings.self

    var body: some View {
        GeometryReader { geometry in
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: .spacingLG) {
                    headerSection

                    Text(troubleText)
                        .fontOpenSans(.body2)
                        .multilineTextAlignment(.leading)
                        .foregroundColor(theme.textBody)

                    if let buttonTitle {
                        ButtonView(text: buttonTitle, type: .inlineTextPrimary, size: .large, isDisabled: false) {
                            onClick?()
                        }
                        .appAccessibility(id: AccessibilityID.scaleSetupIntroButton)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(minHeight: geometry.size.height, alignment: .center)
            }
        }
    }

    private var headerSection: some View {
        VStack(spacing: .spacingXS) {
            Image(scale.imgPath)
                .resizable()
                .scaledToFit()
                .frame(width: 180, height: 180)
                .cornerRadius(.radiusLG)
                .themeDropShadow()
                .padding(.bottom, .spacingLG)
                .accessibilityHidden(true)

            Text(scaleSetupLang.modelTitle(introModelCode(for: scale)))
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)

            Text(scale.productName)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
        }
        .accessibilityElement(children: .combine)
    }
    
    private var buttonTitle: String? {
        return  [.espTouchWifi, .wifi].contains(scale.setupType)
        ? scaleSetupLang.getScaleMacAddress
        : nil
    }

    private func introModelCode(for scale: DeviceItemInfo) -> String {
        if scale.setupType == .bpm {
            let primary = primaryBpmSetupSku(for: scale.sku)
            return bpmListModelLabel(primarySku: primary)
        }
        return scale.sku
    }
}

#Preview(body: {
    ScaleSetupIntroView(scale: SCALES[0]) { }
    .padding(.horizontal)
})
