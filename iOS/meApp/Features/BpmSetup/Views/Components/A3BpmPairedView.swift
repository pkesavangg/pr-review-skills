///
///  A3BpmPairedView.swift
///  meApp
///

import SwiftUI

struct A3BpmPairedView: View {
    @Environment(\.appTheme) private var theme

    let onLearnHowToMeasure: () -> Void

    private let lang = BpmSetupStrings.Paired.self

    var body: some View {
        GeometryReader { geometry in
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: .spacingLG) {
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(lang.title)
                            .fontOpenSans(.heading4)
                            .foregroundColor(theme.textHeading)
                            .multilineTextAlignment(.leading)
                            .lineLimit(nil)
                            .fixedSize(horizontal: false, vertical: true)

                        InlineButtonText(
                            prefix: lang.descriptionPrefix,
                            linkText: lang.learnLink,
                            suffix: lang.descriptionSuffix,
                            isUnderlined: false
                        ) {
                            onLearnHowToMeasure()
                        }
                        .accessibilityHint(BpmSetupStrings.A11y.learnHowHint)
                        .appAccessibility(id: AccessibilityID.bpmLearnHowButton)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    AppIconView(icon: AppAssets.checkMarkLarge, size: IconSize(width: 180, height: 180))
                        .foregroundColor(theme.statusSuccess)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.vertical, .spacingXL)
                        .accessibilityLabel(BpmSetupStrings.A11y.pairedSuccessLabel)
                }
                .padding(.horizontal, .spacingSM)
                // MOB-1247: centre setup-slide content to match `ScaleSetupIntroView`.
                .frame(maxWidth: .infinity)
                .frame(minHeight: geometry.size.height, alignment: .center)
            }
        }
    }
}
