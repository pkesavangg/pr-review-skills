//
//  MultipleReadingsToastView.swift
//  meApp
//

import SwiftUI

/// Compact header row rendered at the top of a reading-arrival toast card when more than one
/// reading has arrived in the current session. Shows a bullet, the count of additional readings,
/// and a VIEW button that navigates to History. Rendered via `ToastModel.headerView`.
struct MultipleReadingsToastView: View {
    @Environment(\.appTheme) private var theme

    /// Number of *additional* readings beyond the first one shown.
    let count: Int
    let onView: () -> Void

    var body: some View {
        HStack(spacing: .spacingXS) {
            Text("•")
                .fontOpenSans(.body4)
                .foregroundColor(theme.textSubheading)

            Text(DashboardStrings.moreReadingsReceived(count))
                .fontOpenSans(.body4)
                .foregroundColor(theme.textSubheading)
                .fixedSize(horizontal: false, vertical: true)

            Spacer()

            Button(action: onView) {
                Text(DashboardStrings.readingArrivalView)
                    .fontOpenSans(.body4)
                    .bold()
                    .foregroundStyle(theme.actionPrimary)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, .spacingXS)
        .padding(.vertical, .spacingXS)
        .frame(maxWidth: .infinity)
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusSM)
    }
}
