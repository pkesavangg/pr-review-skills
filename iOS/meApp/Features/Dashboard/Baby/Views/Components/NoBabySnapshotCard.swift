//
//  NoBabySnapshotCard.swift
//  meApp
//
//  Empty state card shown in the multi-device snapshot overview when a baby
//  scale is paired but no baby profile has been created yet.
//

import SwiftUI

struct NoBabySnapshotCard: View {
    @Environment(\.appTheme) private var theme
    let onAddBaby: () -> Void

    var body: some View {
        VStack(spacing: .spacingMD) {
            Image(AppAssets.babyAppIcon)
                .resizable()
                .scaledToFit()
                .frame(width: 56, height: 56)

            VStack(spacing: .spacingXS) {
                Text(BabyDashboardStrings.noBabiesTitle)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                    .multilineTextAlignment(.center)

                Text(BabyDashboardStrings.noBabiesSubtitle)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }

            ButtonView(
                text: BabyDashboardStrings.addBaby,
                type: .filledPrimary,
                size: .large,
                isDisabled: false,
                action: onAddBaby
            )
        }
        .padding(.horizontal, .spacingMD)
        .padding(.vertical, .spacingXL)
        .frame(maxWidth: .infinity)
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusSM)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(BabyDashboardStrings.noBabiesTitle)
    }
}
