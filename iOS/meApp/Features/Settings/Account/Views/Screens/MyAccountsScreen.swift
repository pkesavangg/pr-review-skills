//  MyAccountsScreen.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 25/06/25.

import SwiftUI

// MARK: - My Accounts Screen
/// Displays a list of existing user accounts and allows switching, adding or deleting them.
/// Uses `UserListItemView` for each row, mirroring the design shown in Figma.
struct MyAccountsScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var router: Router<SettingsRoute>
    @EnvironmentObject private var settingsStore: SettingsStore
    @EnvironmentObject private var tabViewModel: BottomTabBarViewModel
    @StateObject private var accountsStore = AccountsStore()
    @State private var openItemID: UUID?

    private let strings = MyAccountsStrings.self
    var body: some View {
        VStack(spacing: 0) {
            // MARK: Header
            NavbarHeaderView<AppIconView, EmptyView>(
                title: strings.title,
                leadingContent: { AppIconView(icon: AppAssets.chevronLeft) },
                onLeadingTap: { router.navigateBack() },
                canShowBorder: true
            )
            ZStack {
                theme.backgroundSecondary.ignoresSafeArea()
                List {
                    accountList
                    loginCTA
                    signupCTA
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
            }
        }
        .onAppear {
            accountsStore.onAccountSwitchSuccess = {
                // All three account-entry paths (switch/login/signup) change the active
                // account, so SettingsScreen's `activeAccount` onChange pops the Settings
                // stack to root — that's the single source of truth for the reset. No
                // explicit navigateBack() here, otherwise it would double-pop.
                tabViewModel.selectedTab = .dash
            }
        }
        .sheet(isPresented: $accountsStore.canShowLoginScreen) {
            LoginScreen(
                prefilledEmail: accountsStore.emailForLogin,
                isFromAccountSwitching: true
            ) {
                // Reset handled by SettingsScreen's activeAccount onChange (see above).
                accountsStore.canShowLoginScreen = false
                tabViewModel.selectedTab = .dash
            }
                .interactiveDismissDisabled()
        }
        .sheet(isPresented: $accountsStore.canShowAccountSignupScreen) {
            SignupScreen(
                isFromAccountSwitching: true
            ) {
                // Reset handled by SettingsScreen's activeAccount onChange (see above).
                accountsStore.canShowAccountSignupScreen = false
                tabViewModel.selectedTab = .dash
            }
                .interactiveDismissDisabled()
        }
        .navigationBarHidden(true)
        .screenAccessibilityRoot(AccessibilityID.myAccountsScreenRoot)
    }

    // MARK: Account List
    @ViewBuilder
    private var accountList: some View {
        Section {
            ForEach(accountsStore.userItems) { account in
                UserListItemView(
                    user: account,
                    openItemID: $openItemID,
                    iconSize: 32,
                    swipeButtonWidth: 56,
                    onTap: { id, needsLogin in
                        if needsLogin {
                            accountsStore.handleLoginCTA(email: account.email, isUserExpired: account.isExpired)
                        } else {
                            accountsStore.switchActiveAccount(to: id)
                        }
                    },
                    onDelete: { _ in
                        accountsStore.userRemoveHandler(user: account)
                    }
                )
                .listRowInsets(top: 0, bottom: 0, leading: 0, trailing: 0)
                .accessibilityElement(children: .contain)
                .appAccessibility(id: AccessibilityID.accountCardRow + "_" + account.accountID)
            }
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }

    // MARK: Buttons
    private var loginCTA: some View {
        VStack(alignment: .center, spacing: .spacingLG) {
            ButtonView(text: strings.logIntoExistingAccount, type: .outlinedPrimary, size: .large, isDisabled: false) {
                accountsStore.handleLoginCTA()
            }
            .appAccessibility(id: AccessibilityID.myAccountsLoginButton)
        }
        .frame(maxWidth: .infinity)
        .listRowInsets(top: 0, bottom: 0, leading: 0, trailing: 0)
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }

    private var signupCTA: some View {
        VStack(alignment: .center, spacing: .spacingLG) {
            ButtonView(text: strings.createNewAccount, type: .inlineTextPrimary, size: .large, isDisabled: false) {
                accountsStore.handleSignupCTA()
            }
            .appAccessibility(id: AccessibilityID.myAccountsSignupButton)
        }
        .frame(maxWidth: .infinity)
        .listRowInsets(top: 0, bottom: 0, leading: 0, trailing: 0)
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
        .padding(.top, .spacingXS)
    }
}

// MARK: - Preview
#Preview {
    MyAccountsScreen()
        .environmentObject(Router<SettingsRoute>())
        .environmentObject(Theme.shared)
        .environmentObject(SettingsStore())
}
