//
//  BabyReadingAssignedToastView.swift
//  meApp
//

import SwiftUI

// MARK: - Shared weight text builder

/// Parses a raw weight string into styled SwiftUI `Text` with numbers large+colored
/// and unit words normal+dark. Used by the arrival toast, assigned toast, and assign modal.
/// - Numbers: 28pt bold, first number in `babyPrimary`, second in `actionSuccess`
/// - Units / suffix tokens: body2, `textHeading` color
func styledBabyWeightText(_ weightString: String, theme: AppColors.Palette) -> Text {
    let tokens = weightString.components(separatedBy: " ")
    var isFirstNumber = true
    return tokens.reduce(Text("")) { acc, token in
        if Double(token) != nil || Int(token) != nil {
            let color = isFirstNumber ? theme.babyPrimary : theme.actionSuccess
            isFirstNumber = false
            return acc + Text(token)
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(color)
        } else {
            return acc + Text(" \(token)")
                .fontOpenSans(.body2)
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
                .fontOpenSans(.body2)
                .foregroundColor(theme.actionSuccess)
            + Text(babyName.uppercased())
                .fontOpenSans(.body2)
                .bold()
                .foregroundColor(theme.actionSuccess))

            HStack(spacing: .spacingXS) {
                Text(DashboardStrings.babyReadingWrongBaby)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)
                    .lineLimit(1)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer()
                Button(action: onReassign) {
                    Text(DashboardStrings.babyReadingReassign)
                        .fontOpenSans(.body1)
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
