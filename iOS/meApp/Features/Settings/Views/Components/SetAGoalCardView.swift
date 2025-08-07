//
//  SetAGoalCardView.swift
//  meApp
//
//  Created by Cursor Bot on 05/08/25.
//

import SwiftUI

struct SetAGoalCardView: View {
    @Environment(\.appTheme) private var theme
    let appAssets = AppAssets.self
    
    let onClose: () -> Void
    let onSetGoal: () -> Void
    
    // MARK: - Strings
    private let lang = SetGoalCardStrings.self
    
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                Button(action: onClose) {
                    AppIconView(icon: appAssets.xmark, size: IconSize(width: 22, height: 20))
                        .foregroundColor(theme.statusIconPrimary)
                }
            }
            .padding(.bottom, .spacingXS)
            
            VStack(spacing: .spacingSM) {
                Text(lang.title)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                
                VStack(alignment: .center, spacing: .spacingMD) {
                    Text(lang.description)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                    ButtonView(
                        text: lang.buttonTitle,
                        type: .filledPrimary,
                        size: .large,
                        isDisabled: false,
                        action: onSetGoal
                    )
                }
            }
        }
        .padding(.spacingMD)
        .background(theme.backgroundSecondary)
        .cornerRadius(.radiusXL)
    }
}

// MARK: - Preview
#Preview {
    SetAGoalCardView(onClose: {}, onSetGoal: {})
        .environmentObject(Theme.shared)
}
