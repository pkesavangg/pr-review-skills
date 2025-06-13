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
                        .padding(.top, .spacingSM)
                        .padding(.trailing, .spacingSM)
                }
            }
            .padding(.bottom, .spacingXS)
            
            ThemedImage(name: appAssets.stamp)
                .frame(width: 100, height: 100)
                .padding(.bottom, .spacingLG)
            
            Text(helpLang.question)
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)
                .padding(.bottom, .spacingSM)
            
            Text(helpLang.generalHelp)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
                .multilineTextAlignment(.center)
                .padding(.horizontal, .spacingMD)
                .padding(.bottom, .spacingLG)
            
            VStack(alignment: .center, spacing: .spacingMD) {
                HStack(spacing: .spacingXS) {
                    AppIconView(icon: appAssets.phone, size: IconSize(width: 20, height: 20))
                        .foregroundColor(theme.actionPrimary)
                    Button {
                        let phone = appConstants.phoneNumber.replacingOccurrences(of: "-", with: "")
                        if let url = URL(string: "tel://\(phone)"),
                           UIApplication.shared.canOpenURL(url) {
                            UIApplication.shared.open(url)
                        }
                    } label: {
                        Text(appConstants.phoneNumber)
                            .fontOpenSans(.link1)
                            .foregroundColor(theme.actionPrimary)
                    }
                }
                HStack(spacing: .spacingXS) {
                    AppIconView(icon: appAssets.email, size: IconSize(width: 20, height: 20))
                        .foregroundColor(theme.actionPrimary)
                    Button {
                        if let url = URL(string: "mailto:\(appConstants.email)"),
                           UIApplication.shared.canOpenURL(url) {
                            UIApplication.shared.open(url)
                        }
                    } label: {
                        Text(appConstants.email)
                            .fontOpenSans(.link1)
                            .foregroundColor(theme.actionPrimary)
                    }
                }
                
                if showGuide {
                    Text(helpLang.viewGuide)
                        .fontOpenSans(.link1)
                        .foregroundColor(theme.actionPrimary)
                        .onTapGesture {
                            onGuide?()
                        }
                }
            }
            .padding(.bottom, .spacingLG)
        }
        .padding(.horizontal, .spacingXS)
        .padding(.vertical, .spacingXS)
        .background(theme.backgroundSecondary)
        .cornerRadius(.radiusXL)
    }
}

// MARK: - Preview
#Preview {
    HelpModalView() {}
        .environmentObject(Theme.shared)
}
