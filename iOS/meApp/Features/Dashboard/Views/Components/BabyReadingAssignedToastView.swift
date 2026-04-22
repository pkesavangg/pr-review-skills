//
//  BabyReadingAssignedToastView.swift
//  meApp
//

import SwiftUI

/// Content view rendered inside the baby-reading-assigned confirmation toast.
/// Shows the weight, the assigned baby name, and a reassign prompt.
struct BabyReadingAssignedToastView: View {
    @Environment(\.appTheme) private var theme

    let weightMessage: String
    let babyName: String
    let onReassign: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            Text(weightMessage)
                .fontOpenSans(.body1)
                .bold()
                .foregroundColor(theme.babyPrimary)

            (Text(DashboardStrings.babyReadingAssignedTo + " ")
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
            + Text(babyName.uppercased())
                .fontOpenSans(.body2)
                .bold()
                .foregroundColor(theme.babyPrimary))

            HStack {
                Text(DashboardStrings.babyReadingWrongBaby)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)
                Spacer()
                ButtonView(
                    text: DashboardStrings.babyReadingReassign,
                    type: .inlineTextPrimary,
                    size: .small,
                    isDisabled: false,
                    action: onReassign
                )
            }
        }
    }
}
