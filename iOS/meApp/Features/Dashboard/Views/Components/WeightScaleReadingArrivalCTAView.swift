//
//  WeightScaleReadingArrivalCTAView.swift
//  meApp
//

import SwiftUI

/// Two-button row rendered inside the weight scale reading arrival toast.
/// Secondary CTA (DISCARD) is on the left; primary CTA (SAVE) is on the right.
struct WeightScaleReadingArrivalCTAView: View {
    let onSave: () -> Void
    let onDiscard: () -> Void

    var body: some View {
        HStack(spacing: .spacingSM) {
            ButtonView(
                text: DashboardStrings.weightReadingArrivalDiscard,
                type: .textPrimary,
                size: .small,
                isDisabled: false,
                action: onDiscard
            )
            Spacer()
            ButtonView(
                text: DashboardStrings.weightReadingArrivalSave,
                type: .filledPrimary,
                size: .small,
                isDisabled: false,
                action: onSave
            )
        }
    }
}
