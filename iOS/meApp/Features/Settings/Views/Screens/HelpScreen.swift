//  HelpScreen.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 24/06/25.

import SwiftUI

// MARK: - Help Screen
struct HelpScreen: View {
    @Environment(\.appTheme) private var theme
    @StateObject var helpStore = HelpStore()
    @EnvironmentObject var router: Router<SettingsRoute>
    
    private let lang = HelpScreenStrings.self
    
    var body: some View {
        VStack(spacing: 0) {
            // Header
            NavbarHeaderView<Image, EmptyView>(
                title: lang.title,
                leadingContent: { Image(AppAssets.chevronLeft) },
                onLeadingTap: { router.navigateBack() },
                onTitleTap: {
                    helpStore.handleHeaderTap()
                },
                canShowBorder: true
            )
            
            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: .spacingLG) {
                    talkToTeamSection()
                        .padding(.horizontal, .spacingSM)
                    digitalManualSection()
                }
                .padding(.vertical, .spacingLG)
                .padding(.bottom, .spacingXL)
            }
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarHidden(true)
        .inAppBrowser(
            url: helpStore.productURL ?? URL(string: AppConstants.Product.baseURL)!,
            isPresented: $helpStore.showProductBrowser
        )
        // Debug menu sheet uses store's flag
        .sheet(isPresented: $helpStore.showDebugMenu,
               onDismiss: { helpStore.dismissDebugMenu() }) {
            TroubleShootingView()
                .environmentObject(helpStore)
        }
    }
    
    // MARK: Sections
    private func talkToTeamSection() -> some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            Text(lang.talkToOurTeamTitle)
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)
            Text(lang.talkToOurTeamSub)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
            
            VStack(alignment: .leading, spacing: .spacingMD) {
                CallButtonView()
                EmailButtonView()
            }
            .padding(.top, .spacingXS)
        }
    }
    
    private func digitalManualSection() -> some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            Group {
                Text(lang.digitalManualTitle)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                Text(lang.digitalManualSub)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)
            }
            .padding(.horizontal, .spacingSM)
            // Scale list with segmented filter
            ScaleManualListView() { scale in
                helpStore.openProductManual(sku: scale.sku)
            }
            .padding(.top, .spacingSM)
        }
    }
}

// MARK: - Preview
#Preview {
    HelpScreen()
        .environmentObject(HelpStore())
        .environmentObject(Router<SettingsRoute>())
}
