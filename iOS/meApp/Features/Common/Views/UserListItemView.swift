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
    var openItemID: Binding<UUID?>? // Optional binding to track open item for swipeable actions
    var iconSize: CGFloat = 32
    /// Width of each swipe action button; allows per-screen customization
    var swipeButtonWidth: CGFloat = 72
    var onTap: ((String, Bool) -> Void)
    var onDelete: ((String) -> Void)? = nil // optional deletion trigger
    
    var body: some View {
        rowContent
            .contentShape(Rectangle()) // Makes entire row tappable
            .onTapGesture {
                onTap(user.accountID, user.isExpired)
            }
            .swipeableActions(
                buttonWidth: swipeButtonWidth,
                buttons:
                    !user.canShowSelection || onDelete == nil ? [] : [
                        SwipeButton(
                            tint: theme.textError,
                            action: { onDelete?(user.accountID) },
                            label: {
                                AnyView(
                                    AppIconView(icon: AppAssets.trash, size: IconSize(width: 24, height: 24))
                                        .foregroundColor(theme.backgroundPrimary)
                                )
                            }
                        )
                    ],
                itemID: user.id,
                openItemID: openItemID,
                openThresholdFraction: 0.1,
                closeWithoutAnimationOnAction: true
            )
    }
    
    private var rowContent: some View {
        HStack(spacing: .spacingSM) {
            Color.clear
                .frame(width: iconSize, height: iconSize)
                .background {
                    let firstInitial = user.name.firstAlphabeticCharacter().uppercased()
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
                    if openItemID?.wrappedValue != user.id {
                        onTap(user.accountID, user.isExpired)
                    }
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
        .init(accountID: "567", name: "Kesavan", email: "kesavan@gmail.com", isSelected: false, isExpired: false, canShowSelection: true),
        .init(accountID: "Random", name: "Random", email: "Random@gmail.com", isSelected: false, isExpired: false, canShowSelection: true),
        .init(accountID: "abc", name: "Kristin", email: "kristin@gmail.com", isSelected: false, isExpired: false, canShowSelection: true),
        .init(accountID: "123",name: "William", email: "william@gmail.com", isSelected: true, isExpired: false, canShowSelection: true),
        .init(accountID: "xyz",name: "Jacob", email: "jacob@gmail.com", isSelected: false, isExpired: true, canShowSelection: true)
    ]
    
    @State private var showDeleteAlert = false
    @State private var userToDelete: UserItemInfo?
    @State private var openItemID: UUID? = nil
    
    var body: some View {
        List {
            ForEach(accounts) { account in
                UserListItemView(
                    user: account,
                    openItemID: $openItemID, // Binding to track open item and closes the other open item
                    onTap: { _, isFromLogin in
                        print("\(account.name) tapped", isFromLogin)
                    },
                    onDelete: { _ in
                        print("Delete tapped for \(account.name)")
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
