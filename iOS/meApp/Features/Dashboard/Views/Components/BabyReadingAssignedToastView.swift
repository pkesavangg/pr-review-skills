//
//  BabyReadingAssignedToastView.swift
//  meApp
//

import SwiftUI

// MARK: - Shared weight text builder

/// Parses a raw weight string into styled SwiftUI `Text` with numbers large+colored
/// and unit words normal+dark. Used by the arrival toast, assigned toast, and assign modal.
/// - `numberStyle`: typography style for numeric tokens (default `.heading4`, 24pt bold)
/// - `unitStyle`: typography style for unit/text tokens (default `.body2`, 16pt regular)
func styledBabyWeightText(
    _ weightString: String,
    theme: AppColors.Palette,
    numberStyle: CustomTextStyle = .heading4,
    unitStyle: CustomTextStyle = .body2
) -> Text {
    let tokens = weightString.components(separatedBy: " ")
    var isFirstNumber = true
    return tokens.reduce(Text("")) { acc, token in
        if Double(token) != nil || Int(token) != nil {
            let color = isFirstNumber ? theme.babyScaleColor : theme.actionSuccess
            isFirstNumber = false
            return acc + Text(token)
                .fontOpenSans(numberStyle)
                .foregroundColor(color)
        } else {
            return acc + Text(" \(token)")
                .fontOpenSans(unitStyle)
                .foregroundColor(theme.textHeading)
        }
    }
}

// MARK: - Assigned toast content view

/// Content view rendered inside the baby-reading-assigned confirmation toast.
struct BabyReadingAssignedToastView: View {
    @Environment(\.appTheme) private var theme

    let weightString: String
    let babyName: String
    let onReassign: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            styledBabyWeightText(weightString, theme: theme)

            (Text(DashboardStrings.babyReadingAssignedTo + " ")
                .fontOpenSans(.body3)
                .foregroundColor(theme.actionSuccess)
            + Text(babyName.uppercased())
                .fontOpenSans(.body3)
                .bold()
                .foregroundColor(theme.actionSuccess))

            HStack(spacing: .spacingXS) {
                Text(DashboardStrings.babyReadingWrongBaby)
                    .fontOpenSans(.body4)
                    .foregroundColor(theme.textBody)
                    .lineLimit(1)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer()
                Button(action: onReassign) {
                    Text(DashboardStrings.babyReadingReassign)
                        .fontOpenSans(.body3)
                        .bold()
                        .underline()
                        .foregroundColor(theme.textHeading)
                }
                .buttonStyle(.plain)
            }
            .padding(.spacingXS)
            .background(theme.backgroundPrimary)
            .cornerRadius(.radiusSM)
        }
    }
}
