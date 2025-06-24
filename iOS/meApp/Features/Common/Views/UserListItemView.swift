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
    
    let item: UserItemInfo
    var iconSize: CGFloat = 32
    var onTap: ((Bool) -> Void)
    
    // MARK: - Body
    var body: some View {
        Button {
            onTap(item.isDisabled)
        } label: {
            rowContent
        }
    }
    
    // MARK: - Row Content
    private var rowContent: some View {
        HStack(spacing: .spacingSM) {
            Color.clear
                .frame(width: iconSize, height: iconSize)
                .background {
                    let firstInitial = item.name.first?.uppercased() ?? ""
                    InitialIconView(
                        character: firstInitial,
                        textColor: theme.backgroundPrimary,
                        backgroundColor: theme.statusIconPrimary,
                        size: iconSize,
                        style: item.isSelected ? .fill : .outline
                    )
                }
                .opacity(item.isDisabled ? 0.4 : 1) // TODO: Need UX for the disabled state
            
            VStack(alignment: .leading, spacing: 0) {
                Text(item.name)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)
                Text(item.email)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
            }
            .opacity(item.isDisabled ? 0.4 : 1) // TODO: Need UX for the disabled state
            
            Spacer()
            if item.isDisabled {
                ButtonView(text: CommonStrings.logIn, type: .inlineTextPrimary, size: .large, isDisabled: false) {
                    onTap(true)
                }
            } else {
                if item.canShowSelection && !item.isDisabled {
                    AppIconView(icon: item.isSelected ? AppAssets.circleCheckFilled : AppAssets.circleOutline, size: IconSize(width: 24, height: 24))
                        .foregroundColor(theme.statusIconPrimary)
                }
            }
        }
        .padding(.spacingSM)
        .background(theme.backgroundPrimary)
        .frame(height: 72)
    }
}

struct AccountListView: View {
    @Environment(\.appTheme) private var theme
    var body: some View {
        List {
            UserListItemView(
                item: .init(name: "Kristin", email: "kristin@gmail.com", isSelected: false, isDisabled: false, canShowSelection: true)
            ) { isFromLogin in
                print("Kristin tapped", isFromLogin)
            }
            .settingsRowInsets(top: 0, bottom: 0, leading: 0, trailing: 0)
            UserListItemView(
                item: .init(name: "William", email: "william@gmail.com", isSelected: true, isDisabled: false, canShowSelection: true)
            ){ isFromLogin in
                print("william@gmail.com", isFromLogin)
            }
            .settingsRowInsets(top: 0, bottom: 0, leading: 0, trailing: 0)
            UserListItemView(
                item: .init(name: "Jacob", email: "jacob@gmail.com", isSelected: false, isDisabled: true, canShowSelection: true)
            ) { isFromLogin in
                print("Kristin tapped", isFromLogin)
            }
            .settingsRowInsets(top: 0, bottom: 0, leading: 0, trailing: 0)
        }
        .listStyle(.insetGrouped)
    }
}

// MARK: - Preview
#Preview {
    AccountListView()
        .themeable()
        .environmentObject(Theme.shared)
}
