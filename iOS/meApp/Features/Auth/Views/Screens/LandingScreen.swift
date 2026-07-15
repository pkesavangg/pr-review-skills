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
    @State private var openItemID: UUID?
    let lang = LandingScreenStrings.self
    let commonLang = CommonStrings.self
    let itemHeight = 72

    var height: CGFloat {
        CGFloat(min(itemHeight * landingStore.userItems.count, itemHeight * 5))
    }

    // Show the multi-account layout whenever any saved account exists.
    var hasAnyAccounts: Bool {
        !landingStore.userItems.isEmpty
    }

    var body: some View {
        RoutingView(stack: $router.stack) {
            ZStack {
                theme.backgroundSecondary
                    .ignoresSafeArea()
                if !hasAnyAccounts {
                    VStack(alignment: .center) {
                        Spacer()
                            .frame(minHeight: .spacing6XL)

                        MeHealthLogoCard()
                            .padding(.bottom, .spacing2XL)

                        VStack(alignment: .center, spacing: .spacingSM) {

                            ButtonView(text: commonLang.logIn, type: .filledPrimary, size: .large, isDisabled: false) {
                                if landingStore.canAddMoreAccounts() {
                                    router.navigate(to: .login(nil))
                                }
                            }
                            .frame(width: 160)
                            .accessibilityIdentifier(AccessibilityID.landingLogInButton)
                            .accessibilityHint(lang.accLogInHint)

                            ButtonView(text: lang.signUp, type: .outlinedPrimary, size: .large, isDisabled: false) {
                                if landingStore.canAddMoreAccounts() {
                                    router.navigate(to: .signup)
                                }
                            }
                            .frame(width: 160)
                            .accessibilityIdentifier(AccessibilityID.landingSignUpButton)
                            .accessibilityHint(lang.accSignUpHint)
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
                                MeHealthLogoCard()
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
                                                                router.navigate(to: .login(item.email))
                                                            } else {
                                                                landingStore.switchAccount(to: id)
                                                            }
                                                        },
                                                        onDelete: { _ in
                                                            landingStore.removeAccount(user: item)
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
                                .accessibilityIdentifier(AccessibilityID.landingLogInToExistingAccountButton)

                                ButtonView(text: lang.createNewAccount, type: .inlineTextPrimary, size: .large, isDisabled: false) {
                                    if landingStore.canAddMoreAccounts() {
                                        router.navigate(to: .signup)
                                    }
                                }
                                .padding(.bottom, .spacing6XL)
                                .accessibilityIdentifier(AccessibilityID.landingCreateNewAccountButton)
                            }

                        }
                    }

                }

            }
        }
        .environmentObject(router)
        .screenAccessibilityRoot(AccessibilityID.landingScreenRoot)
    }
}

#Preview("1 Account") {
    LandingScreen()
        .environmentObject(Theme.shared)
}
