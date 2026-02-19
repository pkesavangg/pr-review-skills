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
    let itemHeight = 72

    var height: CGFloat {
        CGFloat(min(itemHeight * landingStore.userItems.count, itemHeight * 5))
    }

    // Check if there are any logged-in users
    var hasLoggedInUsers: Bool {
        !landingStore.accounts.isEmpty
    }

    var body: some View {
        RoutingView(stack: $router.stack) {
            ZStack {
                Group {
                    // Show empty landing screen if no logged-in users exist
                    // swiftlint:disable:next empty_count
                    (hasLoggedInUsers && landingStore.userItems.count > 0) ? theme.backgroundSecondary : theme.actionPrimary
                }
                .ignoresSafeArea()
                // Show empty landing screen if no logged-in users exist (even if there are logged-out accounts)
                if !hasLoggedInUsers || landingStore.userItems.isEmpty {
                    VStack(alignment: .center) {
                        Spacer()
                            .frame(minHeight: .spacing6XL)

                        LogoView()
                            .padding(.bottom, 55)

                        VStack(alignment: .center, spacing: .spacingSM){

                            Button(action: {
                                if landingStore.canAddMoreAccounts() {
                                    router.navigate(to: .login(nil))
                                }

                            }, label:{
                                Text(commonLang.logIn.uppercased())
                                    .fontWeight(.bold)
                                    .fontOpenSans(.button1)
                                    .frame(minWidth: 96)
                                    .padding(.vertical, .spacingXS)
                            })
                            .buttonStyle(AppPressableButtonStyle(type: .filledSecondary, size: .large, backgroundColorOverride: nil))

                            ButtonView(text: lang.signUp, type: .outlinedSecondary, size: .large, isDisabled: false) {
                                if landingStore.canAddMoreAccounts() {
                                    router.navigate(to: .signup)
                                }
                            }
                            .frame(width: 96)
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
                            // Fixed logo section — height based on screen height
                            VStack {
                                Spacer()
                                LogoView(isFromAccountSwitching: true)
                                    .padding(.bottom, .spacingMD)
                            }
                            .frame(height: proxy.size.height * 0.33) // consistent across previews

                            VStack {
                                // Scrollable account list and CTAs
                                ScrollView(.vertical) {
                                    VStack(spacing: .spacingXS) {
                                        VStack(spacing: 0) {
                                            ForEach(Array(landingStore.userItems.enumerated()), id: \.element.id) { index, item in
                                                VStack(spacing: 0) {
                                                    UserListItemView(
                                                        user: item,
                                                        openItemID: $openItemID,
                                                        onTap: { id, needsLogin in
                                                            if needsLogin {
                                                                // If the user is expired or logged out, allow login with the same email.
                                                                // If the user modifies the email and the account limit has been reached, show the max accounts alert.
                                                                router.navigate(to: .login(item.email))
                                                            } else {
                                                                landingStore.switchAccount(to: id)
                                                            }
                                                        }
                                                    )
                                                    if index < landingStore.userItems.count - 1 {
                                                        Divider()
                                                            .frame(height: 0.5)
                                                            .background(theme.statusUtilityPrimary)
                                                            .padding(.leading, 56)
                                                    }
                                                }
                                            }
                                        }
                                        .background(theme.backgroundPrimary)
                                        .cornerRadius(.radiusSM)
                                        .padding(.horizontal, .spacingSM)
                                    }
                                }
                                .scrollDisabled(landingStore.userItems.count <= 5) // Disable scrolling if 5 or fewer accounts
                                .frame(height: height)
                                .frame(maxWidth: .infinity)

                                // CTA Buttons
                                ButtonView(text: lang.logInToExistingAccount, type: .outlinedPrimary, size: .large, isDisabled: false) {
                                    if landingStore.canAddMoreAccounts() {
                                        router.navigate(to: .login(nil))
                                    }
                                }
                                .padding(.vertical, .spacingSM)

                                ButtonView(text: lang.createNewAccount, type: .inlineTextPrimary, size: .large, isDisabled: false) {
                                    if landingStore.canAddMoreAccounts() {
                                        router.navigate(to: .signup)
                                    }
                                }
                                .padding(.bottom, .spacing6XL)
                            }

                        }
                    }

                }

            }
        }
        .environmentObject(router)
    }
}

#Preview("1 Account") {
    LandingScreen()
        .environmentObject(Theme.shared)
}
