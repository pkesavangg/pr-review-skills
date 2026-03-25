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
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                AppIconView(icon: AppAssets.checkMarkLarge, size: IconSize(width: 180, height: 180))
                    .foregroundColor(theme.statusSuccess)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, .spacingXL)
            }
            .padding(.top, .spacingLG)
        }
    }
}
