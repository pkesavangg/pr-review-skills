//  AddMultipleAccountsModalView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 30/06/25.
//

import SwiftUI

// MARK: - AddMultipleAccountsModalView
/// Modal presented to highlight the new *multiple-accounts* feature and let users add a secondary account.
struct AddMultipleAccountsModalView: View {
    @Environment(\.appTheme) private var theme
    
    /// First letter of the primary account – rendered in the initial icon.
    let initial: String
    /// Callback when the user dismisses the modal.
    let onClose: () -> Void
    /// Callback when the user taps **Add Account**.
    let onAddAccount: () -> Void
    
    private let strings = AddAccountModalStrings.self
    
    var body: some View {
        VStack(spacing: 0) {
            // Close button – top-right corner
            HStack {
                Spacer()
                Button(action: onClose) {
                    AppIconView(icon: AppAssets.xmarkSmall, size: IconSize(width: 20, height: 20))
                        .foregroundColor(theme.statusIconPrimary)
                }
            }
            .padding(.bottom, .spacingXS)
            
            // Icon cluster (initial + outline icon)
            iconCluster
                .padding(.bottom, .spacingMD)
            // Headline
            Text(strings.title)
                .fontOpenSans(.heading4)
                .multilineTextAlignment(.center)
                .foregroundColor(theme.textHeading)
                .padding(.bottom, .spacingSM)
            
            // Description
            Text(strings.description)
                .fontOpenSans(.body2)
                .multilineTextAlignment(.center)
                .foregroundColor(theme.textBody)
                .padding(.bottom, .spacingLG)
            
            // CTA button
            ButtonView(
                text: strings.addAccount,
                type: .filledPrimary,
                size: .large,
                isDisabled: false,
                action: onAddAccount
            )
        }
        .padding(.spacingMD)
        .background(theme.backgroundSecondary)
        .cornerRadius(.radiusXL)
    }
    
    // MARK: - Icon Cluster
    private var iconCluster: some View {
        ZStack {
            VStack {
                ZStack {
                    AppIconView(icon: AppAssets.userProfile, size: IconSize(width: 60, height: 60))
                        .foregroundColor(theme.statusIconPrimary)
                }
            }
            .padding(.leading, 40)
            
            InitialIconView(
                character: initial,
                textColor: theme.backgroundPrimary,
                backgroundColor: theme.statusIconPrimary,
                size: 54,
                style: .fill
            )
            .overlay {
                Circle()
                    .stroke(theme.backgroundSecondary, lineWidth: 3)
                    .frame(width: 54, height: 54)
            }
            .padding(.trailing, 40)
        }
        .frame(height: 100)
    }
}

// MARK: - Preview
#Preview {
    AddMultipleAccountsModalView(initial: "K", onClose: {}, onAddAccount: {})
        .environmentObject(Theme.shared)
        .padding(.horizontal) // gives some breathing room in canvas
}
