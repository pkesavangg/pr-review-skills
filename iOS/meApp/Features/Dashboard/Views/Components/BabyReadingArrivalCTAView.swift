//
//  BabyReadingArrivalCTAView.swift
//  meApp
//

import SwiftUI

/// Weight + timestamp + two-button row rendered inside the baby scale reading arrival toast.
/// When `babyName` is provided (single-baby case), the baby's name is shown below the weight
/// so the user knows exactly which baby will receive the reading on ASSIGN.
struct BabyReadingArrivalCTAView: View {
    @Environment(\.appTheme) private var theme

    let weightString: String
    let timestamp: String
    /// Non-nil when only one baby profile exists; surfaces the name so the user can
    /// confirm assignment without opening the full selection modal.
    let babyName: String?
    let onAssign: () -> Void
    let onDiscard: () -> Void

    init(
        weightString: String,
        timestamp: String,
        babyName: String? = nil,
        onAssign: @escaping () -> Void,
        onDiscard: @escaping () -> Void
    ) {
        self.weightString = weightString
        self.timestamp = timestamp
        self.babyName = babyName
        self.onAssign = onAssign
        self.onDiscard = onDiscard
    }

    var body: some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            (styledBabyWeightText(weightString, theme: theme)
            + Text(" - \(timestamp)")
                .fontOpenSans(.body2)
                .foregroundColor(theme.textHeading))

            if let babyName {
                Text("\(DashboardStrings.babyReadingForBaby) \(babyName)")
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)
            }

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
