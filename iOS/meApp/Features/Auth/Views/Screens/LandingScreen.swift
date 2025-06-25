//
//  LandingScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 16/06/25.
//

import SwiftUI

struct LandingScreen: View {
    @Environment(\.appTheme) var theme
    @EnvironmentObject var themeManager: Theme
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var router = Router<AuthRoute>()
    @StateObject private var landingStore = LandingStore()
    @State private var openItemID: UUID? = nil
    let lang = LandingScreenStrings.self
    let commonLang = CommonStrings.self
    
    var body: some View {
        RoutingView(stack: $router.stack) {
            ZStack {
                Group {
                    userItems.count > 0 ? theme.backgroundSecondary : theme.actionPrimary
                }
                .ignoresSafeArea()
                if !(userItems.count > 0) {
                    VStack(alignment: .center) {
                        Spacer()
                            .frame(minHeight: .spacing6XL)
                        
                        LogoView()
                            .padding(.bottom, 55)
                        
                        VStack(alignment: .center, spacing: .spacingSM){
                            ButtonView(text: commonLang.logIn, type: .filledSecondary, size: .large, isDisabled: false, action: {router.navigate(to: .login(nil))})
                            ButtonView(text: lang.signUp, type: .outlinedSecondary, size: .large, isDisabled: false, action: {router.navigate(to: .signup)})
                        }
                        .padding(.bottom, .spacing6XL)
                        
                        Spacer()
                            .frame(minHeight: .spacing6XL)
                        
                        VersionView()
                    }
                } else {
                    // Layout when accounts exist – logo stays above the halfway mark, list & buttons scroll underneath
                    GeometryReader { proxy in
                        VStack(spacing: 0) {
                            // Logo section occupies ~45 % of the screen height so it always remains above half
                            VStack {
                                Spacer()
                                LogoView(isFromAccountSwitching: true)
                                Spacer()
                            }
                            .frame(height: proxy.size.height * 0.3)
                            .frame(maxWidth: .infinity)
                            
                            // Scrollable content for accounts & CTA buttons
                            ScrollView(.vertical, showsIndicators: false) {
                                VStack(spacing: .spacingXS) {
                                    VStack(spacing: 0) {
                                        ForEach(Array(userItems.enumerated()), id: \.element.id) { index, item in
                                            VStack(spacing: 0) {
                                                UserListItemView(
                                                    user: item,
                                                    openItemID: $openItemID,
                                                    onTap: { id, isExpired in
                                                        if isExpired {
                                                            router.navigate(to: .login(item.email))
                                                        } else {
                                                            landingStore.switchAccount(to: id)
                                                        }
                                                    }
                                                )
                                                // Show divider only if not the last item
                                                if index < userItems.count - 1 {
                                                    Divider()
                                                        .frame(height: 0.5)
                                                        .background(theme.statusUtility)
                                                        .padding(.leading, 56) // Adjust to align with avatar/text
                                                }
                                            }
                                        }
                                    }
                                    .background(theme.backgroundPrimary)
                                    .cornerRadius(.radiusSM)
                                    .padding(.horizontal, .spacingSM)
                                    ButtonView(text: lang.logInToExistingAccount, type: .outlinedPrimary, size: .large, isDisabled: false) {
                                        router.navigate(to: .login(nil))
                                    }
                                    ButtonView(text: lang.createNewAccount, type: .inlineTextPrimary, size: .large, isDisabled: false) {
                                        router.navigate(to: .signup)
                                    }
                                }
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                }
                
            }
        }
        .environmentObject(router)
    }
}

#Preview {
    LandingScreen()
        .environmentObject(Theme.shared)
}

// MARK: - Helpers
private extension LandingScreen {
    var userItems: [UserItemInfo] {
        
        let sortedAccounts = landingStore.accounts.sorted { lhs, rhs in
            let lhsDate = DateTimeTools.parse(lhs.lastActiveTime ?? "") ?? .distantPast
            let rhsDate = DateTimeTools.parse(rhs.lastActiveTime ?? "") ?? .distantPast
            return lhsDate > rhsDate
        }
        
        
        return sortedAccounts.map { account in
            UserItemInfo(
                accountID: account.accountId,
                name: account.firstName?.isEmpty == false ? account.firstName! : account.email,
                email: account.email,
                isSelected: false,
                isExpired: account.isExpired ?? false,
                canShowSelection: false
            )
        }
        
    }
}
