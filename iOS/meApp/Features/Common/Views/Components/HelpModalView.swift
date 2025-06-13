import SwiftUI

struct HelpModalView: View {
    var showGuide: Bool = false
    let onClose: () -> Void
    var onGuide: (() -> Void)?
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var themeManager: Theme
    let appAssets = AppAssets.self
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                Button(action: onClose) {
                    AppIconView(icon: AppAssets.xmark, size: IconSize(width: 22, height: 20))
                        .foregroundColor(theme.statusIconPrimary)
                        .padding(.top, .spacingSM)
                        .padding(.trailing, .spacingSM)
                }
            }
            .padding(.bottom, .spacingMD)
                
            Image(themeManager.isDarkMode ? appAssets.stampDark : appAssets.closeCircle)
                .resizable()
                .scaledToFit()
                .frame(width: 100, height: 100)
                .padding(.bottom, .spacingLG)
            
            Text(HelpStrings.question)
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)
                .padding(.bottom, .spacingSM)
            
            Text(HelpStrings.generalHelp)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
                .multilineTextAlignment(.center)
                .padding(.horizontal, .spacingMD)
                .padding(.bottom, .spacingLG)
            
            VStack(alignment: .center, spacing: .spacingMD) {
                HStack(spacing: .spacingXS) {
                    AppIconView(icon: appAssets.phone, size: IconSize(width: 20, height: 20))
                        .foregroundColor(theme.actionPrimary)
                    Text(AppConstants.Help.phoneNumber)
                        .fontOpenSans(.link1)
                        .foregroundColor(theme.actionPrimary)
                        .onTapGesture {
                            let phone = AppConstants.Help.phoneNumber.replacingOccurrences(of: "-", with: "")
                            if let url = URL(string: "tel://\(phone)"),
                               UIApplication.shared.canOpenURL(url) {
                                UIApplication.shared.open(url)
                            }
                        }
                }
                HStack(spacing: .spacingXS) {
                    AppIconView(icon: appAssets.email, size: IconSize(width: 20, height: 20))
                        .foregroundColor(theme.actionPrimary)
                    Text(AppConstants.Help.email)
                        .fontOpenSans(.link1)
                        .foregroundColor(theme.actionPrimary)
                        .onTapGesture {
                            if let url = URL(string: "mailto:\(AppConstants.Help.email)"),
                               UIApplication.shared.canOpenURL(url) {
                                UIApplication.shared.open(url)
                            }
                        }
                }
                
                if showGuide {
                    Text(HelpStrings.viewGuide)
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
