//
//  WeightScaleReadingArrivalCTAView.swift
//  meApp
//

import SwiftUI

/// Value row + two-button row rendered inside the weight scale reading arrival toast.
/// Renders the weight value at 24px/bold, unit at 16px/regular, timestamp at 12px/regular.
/// Secondary CTA (DISCARD) is on the left; primary CTA (SAVE) is on the right.
struct WeightScaleReadingArrivalCTAView: View {
    @Environment(\.appTheme) private var theme

    let weightWithUnit: String
    let timestamp: String
    let onSave: () -> Void
    let onDiscard: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: .spacingSM) {
            weightValueText
            HStack(spacing: .spacingSM) {
                ButtonView(
                    text: DashboardStrings.weightReadingArrivalDiscard,
                    type: .textPrimary,
                    size: .small,
                    isDisabled: false,
                    action: onDiscard
                )
                .appAccessibility(id: AccessibilityID.weightReadingDiscardButton)
                Spacer()
                ButtonView(
                    text: DashboardStrings.weightReadingArrivalSave,
                    type: .filledPrimary,
                    size: .small,
                    isDisabled: false,
                    action: onSave
                )
                .appAccessibility(id: AccessibilityID.weightReadingSaveButton)
            }
        }
    }

    private var weightValueText: Text {
        let parts = weightWithUnit.split(separator: " ", maxSplits: 1).map(String.init)
        let value = parts.first ?? weightWithUnit
        let unit = parts.count > 1 ? parts[1] : ""
        return Text(value)
            .fontOpenSans(.heading4)
            .foregroundColor(theme.textHeading)
        + Text(unit.isEmpty ? "" : " \(unit)")
            .fontOpenSans(.body2)
            .foregroundColor(theme.textHeading)
        + Text("  \(timestamp)")
            .fontOpenSans(.body4)
            .foregroundColor(theme.textSubheading)
    }
}
