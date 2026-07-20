//
//  BabyReadingArrivalCTAView.swift
//  meApp
//

import SwiftUI

/// Weight + timestamp + two-button row rendered inside the baby scale reading arrival toast
/// when multiple baby profiles exist. The user selects ASSIGN to open the baby-selection modal
/// or DON'T ASSIGN to discard the reading.
/// For the single-baby case the toast uses `WeightScaleReadingArrivalCTAView` with a
/// personalized title ("New Reading Received for [NAME]") instead.
struct BabyReadingArrivalCTAView: View {
    @Environment(\.appTheme) private var theme

    let weightString: String
    let timestamp: String
    let onAssign: () -> Void
    let onDiscard: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            (styledBabyWeightText(weightString, theme: theme)
            + Text("  \(timestamp)")
                .fontOpenSans(.body4)
                .foregroundColor(theme.textSubheading))

            HStack(spacing: .spacingSM) {
                ButtonView(
                    text: DashboardStrings.babyReadingArrivalDontAssign,
                    type: .textPrimary,
                    size: .small,
                    isDisabled: false,
                    action: onDiscard
                )
                .appAccessibility(id: AccessibilityID.babyReadingDontAssignButton)
                Spacer()
                ButtonView(
                    text: DashboardStrings.babyReadingArrivalAssign,
                    type: .filledPrimary,
                    size: .small,
                    isDisabled: false,
                    action: onAssign
                )
                .appAccessibility(id: AccessibilityID.babyReadingAssignButton)
            }
        }
    }
}
