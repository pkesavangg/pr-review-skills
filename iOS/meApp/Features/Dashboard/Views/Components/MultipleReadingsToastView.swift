//
//  MultipleReadingsToastView.swift
//  meApp
//

import SwiftUI

/// Rendered inside the reading-arrival toast when more than one reading arrives while
/// the card is already visible. Shows a count of additional readings and a VIEW button
/// that navigates to History where all readings can be reviewed.
struct MultipleReadingsToastView: View {
    @Environment(\.appTheme) private var theme

    /// Number of *additional* readings beyond the first one shown.
    let count: Int
    let onView: () -> Void

    var body: some View {
        HStack(spacing: .spacingSM) {
            Text(DashboardStrings.moreReadingsReceived(count))
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
                .fixedSize(horizontal: false, vertical: true)
            Spacer()
            ButtonView(
                text: DashboardStrings.readingArrivalView,
                type: .filledPrimary,
                size: .small,
                isDisabled: false,
                action: onView
            )
        }
    }
}
