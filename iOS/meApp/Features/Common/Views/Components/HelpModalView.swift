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
    // MARK: - Product Manual Browser State
    @State var showProductBrowser: Bool = false
    @State var productURL: URL? = nil
    
    var skuToNavigate: String?
    let onClose: () -> Void
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
                Image(themeManager.isDarkMode ? appAssets.ggLogoLight : appAssets.ggLogoLarge)
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
                if let sku = skuToNavigate {
                    ButtonView(text: helpLang.gettingStartedGuide, type: .inlineTextPrimary, size: .large, isDisabled: false) {
                        openProductManual(sku: sku)
                    }
                }
                CallButtonView()
                EmailButtonView()
            }
            .padding(.top, .spacingLG)
        }
        .padding(.spacingMD)
        .background(theme.backgroundSecondary)
        .cornerRadius(.radiusXL)
        .inAppBrowser(
            url: productURL ?? URL(string: AppConstants.Product.baseURL)!,
            isPresented: $showProductBrowser
        )
    }
    
    /// Presents the in-app browser for the given product SKU.
    func openProductManual(sku: String) {
        guard let url = URL(string: "\(AppConstants.Product.baseURL)/\(sku)") else { return }
        productURL = url
        showProductBrowser = true
    }
}

// MARK: - Preview
#Preview {
    HelpModalView() {}
        .environmentObject(Theme.shared)
        .padding(.horizontal)
}
