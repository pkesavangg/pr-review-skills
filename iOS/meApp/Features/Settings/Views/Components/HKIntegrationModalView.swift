//
//  HKIntegrationModalView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 26/06/25.
//


import SwiftUI

struct HKIntegrationModalView: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var themeManager: Theme
    
    let state: HKIntegrationModalState
    let onClose: () -> Void
    let onPrimaryTap: () -> Void
    let onSecondaryTap: (() -> Void)?
    
    private var content: HKIntegrationModalContent {
        switch state {
        case .outOfSync: return HKIntegrationModalStrings.outOfSync
        case .finishAdding: return HKIntegrationModalStrings.finishAdding
        case .addIntegration: return HKIntegrationModalStrings.addIntegration
        }
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // Close button
            HStack {
                Spacer()
                Button(action: onClose) {
                    AppIconView(icon: AppAssets.xmark, size: IconSize(width: 22, height: 20))
                        .foregroundColor(theme.statusIconPrimary)
                }
            }
            .padding(.bottom, .spacingXS)
            
            VStack(spacing: .spacingSM) {
                ZStack(alignment: .topTrailing) {
                    // Main Health icon
                    Image(content.imageName)
                        .resizable()
                        .frame(width: 100, height: 100)
                    
                    // Top-right exclamation badge for outOfSync
                    if state == .outOfSync {
                        AppIconView(icon: AppAssets.exclamationMark)
                            .foregroundColor(theme.statusError)
                            .offset(x: 14, y: -12) // push slightly outside
                    }
                }
                .frame(width: 100, height: 100)
                .padding(.top, .spacingXS)
                
                // Title
                Text(content.title)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                    .multilineTextAlignment(.center)
                
                // Message
                Group {
                    if let parts = content.attributedParts {
                        (
                            Text(parts.prefix)
                                .fontOpenSans(.body2)
                            +
                            Text(parts.highlight)
                                .fontOpenSans(.heading5)
                            +
                            Text(parts.suffix)
                                .fontOpenSans(.body2)
                        )
                    } else if let message = content.message {
                        Text(message)
                            .fontOpenSans(.body2)
                    }
                }
                .foregroundColor(theme.textBody)
                .multilineTextAlignment(.center)
                .padding(.horizontal, .spacingLG)
                
                // Primary button
                ButtonView(
                    text: content.primaryButtonTitle,
                    type: .filledPrimary,
                    size: .large,
                    isDisabled: false,
                    action: onPrimaryTap
                )
                .padding(.top, .spacingSM)
                
                // Optional secondary button
                if let secondaryTitle = content.secondaryButtonTitle {
                    ButtonView(text: secondaryTitle, type: .inlineTextPrimary, size: .large, isDisabled: false) {
                        onSecondaryTap?()
                    }
                }
            }
        }
        .padding(.spacingMD)
        .background(theme.backgroundSecondary)
        .cornerRadius(.radiusXL)
    }
}

// MARK: - Previews
struct HKIntegrationModalView_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            HKIntegrationModalView(
                state: .outOfSync,
                onClose: {},
                onPrimaryTap: {},
                onSecondaryTap: {}
            )
            .previewDisplayName("Out of Sync")
            .environmentObject(Theme.shared)
            
            HKIntegrationModalView(
                state: .finishAdding,
                onClose: {},
                onPrimaryTap: {},
                onSecondaryTap: nil
            )
            .previewDisplayName("Finish Adding")
            .environmentObject(Theme.shared)
            
            HKIntegrationModalView(
                state: .addIntegration,
                onClose: {},
                onPrimaryTap: {},
                onSecondaryTap: nil
            )
            .previewDisplayName("Add Integration")
            .environmentObject(Theme.shared)
        }
        .padding(.horizontal, .spacingSM)
    }
}
