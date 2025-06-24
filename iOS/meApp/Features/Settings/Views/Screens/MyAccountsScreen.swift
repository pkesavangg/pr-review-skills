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
    
    // Placeholder data – replace with real accounts when wired up.
    @State private var accounts: [UserItemInfo] = [
        .init(accountID: "abc", name: "Kristin", email: "kristin@gmail.com", isSelected: true,  isExpired: false, canShowSelection: true),
        .init(accountID: "123", name: "William", email: "william@gmail.com", isSelected: false, isExpired: false, canShowSelection: true),
        .init(accountID: "xyz", name: "Jacob",   email: "jacob@gmail.com",   isSelected: false, isExpired: true,  canShowSelection: true)
    ]
    
    @State private var showDeleteAlert = false
    @State private var accountPendingDelete: UserItemInfo? = nil
    
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
                    actionButtons
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
            }
        }
        .navigationBarBackButtonHidden(true)
        .alert(strings.deleteAccountTitle, isPresented: $showDeleteAlert, presenting: accountPendingDelete) { item in
            Button(strings.deleteAction, role: .destructive) {
                if let idx = accounts.firstIndex(where: { $0.id == item.id }) {
                    accounts.remove(at: idx)
                }
            }
            Button(strings.cancelAction, role: .cancel) {}
        } message: { item in
            Text(strings.deleteMessagePrefix + item.email + "?")
        }
    }
    
    // MARK: Account List
    private var accountList: some View {
        Section {
            ForEach(accounts) { account in
                UserListItemView(
                    user: account,
                    onTap: { id, isFromLogin in
                        // TODO: Implement account switching / login flow.
                    },
                    onDelete: { _ in
                        accountPendingDelete = account
                        showDeleteAlert = true
                    }
                )
                .listRowInsets(top: 0, bottom: 0, leading: 0, trailing: 0)
            }
        }
    }
    
    // MARK: Buttons
    private var actionButtons: some View {
        VStack(alignment:.center, spacing: .spacingLG) {
            ButtonView(text: strings.logIntoExistingAccount, type: .outlinedPrimary, size: .large, isDisabled: false) {
                // TODO: Implement login flow
            }
            ButtonView(text: strings.createNewAccount, type: .inlineTextPrimary, size: .large, isDisabled: false) {
                // TODO: Implement create new account flow
            }
        }
        .frame(maxWidth: .infinity)
        .listRowInsets(top: 0, bottom: 0, leading: 0, trailing: 0)
        .listRowBackground(Color.clear)
    }
}

// MARK: - Preview
#Preview {
    MyAccountsScreen()
        .environmentObject(Router<SettingsRoute>())
        .environmentObject(Theme.shared)
        .environmentObject(SettingsStore())
}
