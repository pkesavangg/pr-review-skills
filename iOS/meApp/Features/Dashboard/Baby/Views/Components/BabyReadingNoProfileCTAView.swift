//
//  BabyReadingNoProfileCTAView.swift
//  meApp
//

import SwiftUI

/// Weight + timestamp + "ADD A BABY" CTA rendered inside the baby scale reading arrival toast
/// when no baby profile exists. Tapping ADD A BABY deep-links to the add-a-baby flow in Settings.
struct BabyReadingNoProfileCTAView: View {
    @Environment(\.appTheme) private var theme

    let weightString: String
    let timestamp: String
    let onAddBaby: () -> Void
    let onDiscard: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            Text(DashboardStrings.babyReadingNoProfileMessage)
                .fontOpenSans(.body2)
                .foregroundStyle(theme.textBody)
                .fixedSize(horizontal: false, vertical: true)

            (styledBabyWeightText(weightString, theme: theme)
            + Text("  \(timestamp)")
                .fontOpenSans(.body4)
                .foregroundStyle(theme.textSubheading))

            HStack(spacing: .spacingSM) {
                ButtonView(
                    text: DashboardStrings.babyReadingNoProfileDiscard,
                    type: .textPrimary,
                    size: .small,
                    isDisabled: false,
                    action: onDiscard
                )
                .appAccessibility(id: AccessibilityID.babyReadingNoProfileDiscardButton)
                Spacer()
                ButtonView(
                    text: DashboardStrings.babyReadingNoProfileAddBaby,
                    type: .filledPrimary,
                    size: .small,
                    isDisabled: false,
                    action: onAddBaby
                )
                .appAccessibility(id: AccessibilityID.babyReadingNoProfileAddButton)
            }
        }
    }
}
