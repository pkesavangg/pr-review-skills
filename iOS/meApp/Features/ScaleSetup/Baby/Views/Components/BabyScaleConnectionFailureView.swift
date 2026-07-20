//
//  BabyScaleConnectionFailureView.swift
//  meApp
//

import SwiftUI

/// Baby scale connection failure screen — always shows the error state.
/// Shown as a dedicated swiper step (.connectionError) when pairing fails.
struct BabyScaleConnectionFailureView: View {
    var onPairAgain: () -> Void = {}
    var onSupport: () -> Void = {}

    @Environment(\.appTheme) private var theme
    private let lang = BabyScaleSetupStrings.Connection.self

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: .spacingMD) {
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(lang.unableToConnect)
                            .fontOpenSans(.heading4)
                            .fontWeight(.bold)
                            .foregroundColor(theme.textHeading)
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)

                        Text(lang.interferenceMessage)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                            .multilineTextAlignment(.leading)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, .spacingXS)
                    .padding(.top, .spacingMD)

                    Spacer()

                    VStack(spacing: .spacingMD) {
                        ButtonView(
                            text: lang.pairAgain,
                            type: .filledPrimary,
                            size: .large,
                            isDisabled: false,
                            action: onPairAgain
                        )
                        .appAccessibility(id: AccessibilityID.babyConnectionFailurePairAgainButton)

                        ButtonView(
                            text: lang.support,
                            type: .inlineTextPrimary,
                            size: .large,
                            isDisabled: false,
                            action: onSupport
                        )
                        .appAccessibility(id: AccessibilityID.babyConnectionFailureSupportButton)
                    }
                    .padding(.bottom, .spacingLG)
                }
                .frame(minHeight: geometry.size.height)
                .frame(maxWidth: .infinity)
            }
        }
    }
}
