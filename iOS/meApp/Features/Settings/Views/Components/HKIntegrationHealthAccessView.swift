//
//  HKIntegrationHealthAccessView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 26/06/25.
//


import SwiftUI

// MARK: - Apple Health Integration States
/// Enumeration representing the different states of Apple Health integration.
struct HKIntegrationHealthAccessView: View {
    @Environment(\.appTheme) private var theme
    @StateObject private var viewModel = HKIntegrationHealthAccessViewModel()
    let state: AppleHealthIntegrationState
    let commonLang = CommonStrings.self
    let lang = HKIntegrationStrings.self
    let onDismiss: (() -> Void)?
    /// Action executed when the primary button (CONNECT / FINISH / OPEN APPLE HEALTH) is tapped.
    let primaryAction: () -> Void
    
    private var content: HKIntegrationHealthAccessContent {
        switch state {
        case .permissionsAllowed:
            return HKIntegrationHealthAccessStrings.permissionsAllowed
        case .permissionsNotAllowed:
            return HKIntegrationHealthAccessStrings.notConnected // Reuse or create new if needed
        case .integrationComplete:
            return HKIntegrationHealthAccessStrings.integrationComplete
        case .integrationFailed:
            return HKIntegrationHealthAccessStrings.integrationFailed
        case .userConflict:
            return HKIntegrationHealthAccessStrings.userConflict
        }
    }
    
    var body: some View {
        VStack {
            NavbarHeaderView(
                title: lang.healthAccess,
                leadingContent: { Image(AppAssets.xmark) },
                trailingContent: {
                    Button {
                        viewModel.showHelpModal()
                    } label: {
                        Image(AppAssets.helpCircle)
                    }
                },
                onLeadingTap: {
                    onDismiss?()
                },
                onTrailingTap: {},
                canShowBorder: true
            )
            VStack(spacing: 0) {
                // Image
                Image(content.imageName)
                    .resizable()
                    .scaledToFit()
                    .frame(maxWidth: 190, maxHeight: 401)
                    .clipShape(RoundedRectangle(cornerRadius: 12)) // TODO: Need to update the theme radius after UX provided
                    .overlay {
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.black, lineWidth: 5) // TODO: Need to replace with theme color after UX provided
                    }
                    .overlay {
                        if state == .integrationFailed  || state == .userConflict {
                            Color.black.opacity(0.1)
                                .cornerRadius(12)
                            Image(AppAssets.exclamationDanger)
                                .resizable()
                                .scaledToFit()
                                .frame(maxWidth: 50, maxHeight: 50)
                        }
                    }
                    .padding(.top, .spacing2XL)
                
                
                // Title
                Text(content.title)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading) // TODO: Need to replace with theme color after UX provided
                    .multilineTextAlignment(.center)
                    .padding(.top, .spacing2XL)
                    .padding(.horizontal, .spacingSM)
                // Description
                Group {
                    if let parts = content.attributedParts {
                        (
                            Text(parts.prefix)
                                .fontOpenSans(.body2)
                            +
                            Text(parts.highlight)
                                .fontOpenSans(.heading5)
                        )
                    } else if let description = content.description {
                        Text(description)
                            .fontOpenSans(.body2)
                    }
                }
                .foregroundColor(theme.textBody) // Replace with correct color once UX provides it
                .multilineTextAlignment(.center)
                .padding(.vertical, .spacingMD)
                .padding(.horizontal, .spacingLG)
                
                // Button
                ButtonView(text: content.buttonTitle,
                           type: .filledPrimary,
                           size: .large,
                           isDisabled: false,
                           action: primaryAction)
                .padding(.top, .spacingSM)
                
                Spacer()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        
        .background(theme.backgroundSecondary.ignoresSafeArea())
    }
}

// MARK: - Previews
struct AppleHealthIntegrationScreen_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            HKIntegrationHealthAccessView(state: .permissionsAllowed) {
                
            } primaryAction: {
                
            }
            .environmentObject(Theme.shared)
            HKIntegrationHealthAccessView(state: .permissionsNotAllowed) {
                
            } primaryAction: {
                
            }
            .environmentObject(Theme.shared)
            HKIntegrationHealthAccessView(state: .integrationComplete) {
                
            } primaryAction: {
                
            }
            .environmentObject(Theme.shared)
            HKIntegrationHealthAccessView(state: .integrationFailed) {
                
            } primaryAction: {
                
            }
            .environmentObject(Theme.shared)
            HKIntegrationHealthAccessView(state: .userConflict) {
                
            } primaryAction: {
                
            }
            .environmentObject(Theme.shared)
        }
    }
}
