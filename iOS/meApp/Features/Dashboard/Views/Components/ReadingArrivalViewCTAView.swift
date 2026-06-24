//
//  ReadingArrivalViewCTAView.swift
//  meApp
//

import SwiftUI

/// Single-button CTA rendered inside the reading arrival toast for Wi-Fi entries.
/// Wi-Fi entries are already saved server-side so there is nothing to confirm or discard —
/// only a VIEW action that navigates the user to History.
struct ReadingArrivalViewCTAView: View {
    let onView: () -> Void

    var body: some View {
        HStack {
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
