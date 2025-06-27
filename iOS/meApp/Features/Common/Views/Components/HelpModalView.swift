//
//  HelpModalView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 13/06/25.
//
import SwiftUI

// MARK: HelpModalView
/// A view that displays a help modal with a question, general help, phone number, email, and guide
struct HelpModalView: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var themeManager: Theme
    
    var showGuide: Bool = false
    let onClose: () -> Void
    var onGuide: (() -> Void)?
    let appAssets = AppAssets.self
    let appConstants = AppConstants.Help.self
    let helpLang = HelpStrings.self
    
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
                Image(appAssets.ggLogoSmall)
                    .resizable()
                    .frame(width: 100, height: 100)
                
                Text(helpLang.question)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                    .padding(.bottom, .spacingSM)
                
                Text(helpLang.generalHelp)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, .spacingMD)
            }
            
            VStack(spacing: .spacingSM) {
                CallButtonView()
                EmailButtonView()
            }
            .padding(.top, .spacingLG)
            
            // TODO: Need to confirm with UX if we need to show the guide button
            if showGuide {
                Text(helpLang.viewGuide)
                    .fontOpenSans(.link1)
                    .foregroundColor(theme.actionPrimary)
                    .onTapGesture {
                        onGuide?()
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
    HelpModalView() {}
        .environmentObject(Theme.shared)
}
