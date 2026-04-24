//
//  BabyReadingArrivalCTAView.swift
//  meApp
//

import SwiftUI

/// Weight + timestamp + two-button row rendered inside the baby scale reading arrival toast.
struct BabyReadingArrivalCTAView: View {
    @Environment(\.appTheme) private var theme

    let weightString: String
    let timestamp: String
    let onAssign: () -> Void
    let onDiscard: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            (styledBabyWeightText(weightString, theme: theme)
            + Text(" - \(timestamp)")
                .fontOpenSans(.body2)
                .foregroundColor(theme.textHeading))

            HStack(spacing: .spacingSM) {
                ButtonView(
                    text: DashboardStrings.babyReadingArrivalDontAssign,
                    type: .textPrimary,
                    size: .small,
                    isDisabled: false,
                    action: onDiscard
                )
                Spacer()
                ButtonView(
                    text: DashboardStrings.babyReadingArrivalAssign,
                    type: .filledPrimary,
                    size: .small,
                    isDisabled: false,
                    action: onAssign
                )
            }
        }
    }
}
