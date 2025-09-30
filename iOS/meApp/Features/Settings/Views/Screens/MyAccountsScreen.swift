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
    @StateObject private var accountsStore = AccountsStore()
    @State private var openItemID: UUID? = nil
    
    private let strings = MyAccountsStrings.self
    
    var body: some View {
        VStack(spacing: 0) {
            // MARK: Header
            NavbarHeaderView<Image, EmptyView>(
                title: strings.title,
                leadingContent: { Image(AppAssets.chevronLeft) },
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
        .sheet(isPresented: $accountsStore.canShowLoginScreen, content: {
            LoginScreen(prefilledEmail: accountsStore.emailForLogin, isFromAccountSwitching: true)
                .interactiveDismissDisabled()
        })
        .sheet(isPresented: $accountsStore.canShowAccountSignupScreen, content: {
            SignupScreen(isFromAccountSwitching: true)
                .interactiveDismissDisabled()
        })
        .navigationBarBackButtonHidden(true)
    }
    
    // MARK: Account List
    @ViewBuilder
    private var accountList: some View {
        if accountsStore.userItems.count > 1 {
            Section {
                ForEach(accountsStore.userItems) { account in
                    UserListItemView(
                        user: account,
                        openItemID: $openItemID,
                        onTap: { id, isExpired in
                            if isExpired {
                                accountsStore.handleLoginCTA(email: account.email, isUserExpired: true)
                            } else {
                                accountsStore.switchActiveAccount(to: id)
                            }
                        },
                        onDelete: { _ in
                            accountsStore.userRemoveHandler(user: account)
                        }
                    )
                    .listRowInsets(top: 0, bottom: 0, leading: 0, trailing: 0)
                }
            }
            .listRowBackground(theme.backgroundPrimary)
            .listRowSeparatorTint(theme.statusUtilityPrimary)
        }
    }
    
    // MARK: Buttons
    private var loginCTA: some View {
        VStack(alignment: .center, spacing: .spacingLG) {
            ButtonView(text: strings.logIntoExistingAccount, type: .outlinedPrimary, size: .large, isDisabled: false) {
                accountsStore.handleLoginCTA()
            }
            
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
