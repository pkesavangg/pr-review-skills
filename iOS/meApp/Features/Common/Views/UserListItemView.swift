//  UserListItemView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 24/06/25.
//
import SwiftUI

// MARK: - User List Item View
/// A single row used inside the account-switching list.
/// Shows an initial icon, name, e-mail and an optional selection indicator.
struct UserListItemView: View {
    @Environment(\.appTheme) private var theme

    let user: UserItemInfo
    var iconSize: CGFloat = 32
    var onTap: ((String, Bool) -> Void)
    var onDelete: ((String) -> Void)? = nil // optional deletion trigger

    var body: some View {
        Button {
            onTap(user.accountID, user.isExpired)
        } label: {
            rowContent
        }
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            if !user.isSelected, !user.isExpired, let onDelete = onDelete {
                Button(role: .cancel) {
                    onDelete(user.accountID)
                } label: {
                    AppIconView(icon: AppAssets.trash, size: IconSize(width: 24, height: 24))
                        .foregroundColor(theme.backgroundPrimary)
                }
                .tint(theme.textError) // Set the background of the swipe action
            }
        }
    }
    
    private var rowContent: some View {
        HStack(spacing: .spacingSM) {
            Color.clear
                .frame(width: iconSize, height: iconSize)
                .background {
                    let firstInitial = user.name.first?.uppercased() ?? ""
                    InitialIconView(
                        character: firstInitial,
                        textColor: theme.backgroundPrimary,
                        backgroundColor: theme.statusIconPrimary,
                        size: iconSize,
                        style: user.isSelected ? .fill : .outline
                    )
                }
                .opacity(user.isExpired ? 0.4 : 1)

            VStack(alignment: .leading, spacing: 0) {
                Text(user.name)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)
                Text(user.email)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
            }
            .opacity(user.isExpired ? 0.4 : 1)

            Spacer()
            if user.isExpired {
                ButtonView(text: CommonStrings.logIn, type: .inlineTextPrimary, size: .large, isDisabled: false) {
                    onTap(user.accountID, user.isExpired)
                }
            } else if user.canShowSelection {
                AppIconView(icon: user.isSelected ? AppAssets.circleCheckFilled : AppAssets.circleOutline, size: IconSize(width: 24, height: 24))
                    .foregroundColor(theme.statusIconPrimary)
            }
        }
        .padding(.spacingSM)
        .background(theme.backgroundPrimary)
        .frame(height: 72)
    }
}


// Testing Purpose View
struct AccountListView: View {
    @Environment(\.appTheme) private var theme
    @State private var accounts: [UserItemInfo] = [
        .init(accountID: "abc", name: "Kristin", email: "kristin@gmail.com", isSelected: false, isExpired: false, canShowSelection: true),
        .init(accountID: "123",name: "William", email: "william@gmail.com", isSelected: true, isExpired: false, canShowSelection: true),
        .init(accountID: "xyz",name: "Jacob", email: "jacob@gmail.com", isSelected: false, isExpired: true, canShowSelection: true)
    ]

    @State private var showDeleteAlert = false
    @State private var userToDelete: UserItemInfo?

    var body: some View {
        List {
            ForEach(accounts) { account in
                UserListItemView(
                    user: account,
                    onTap: { _, isFromLogin in
                        print("\(account.name) tapped", isFromLogin)
                    },
                    onDelete: { _ in
                        userToDelete = account
                        showDeleteAlert = true
                    }
                )
                .listRowInsets(top: 0, bottom: 0, leading: 0, trailing: 0)
            }
        }
        .listStyle(.insetGrouped)
        .alert("Delete Account", isPresented: $showDeleteAlert, presenting: userToDelete) { item in
            Button("Delete", role: .destructive) {
                if let index = accounts.firstIndex(where: { $0.id == item.id }) {
                    accounts.remove(at: index)
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: { item in
            Text("Are you sure you want to delete the account for \(item.email)?")
        }
    }
}


// MARK: - Preview
#Preview {
    AccountListView()
        .environmentObject(Theme.shared)
}
