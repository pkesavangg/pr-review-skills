//
//  BabyListStepView.swift
//  meApp
//

import SwiftUI

/// A reusable baby list view that shows added babies with swipe-to-delete and an "ADD A BABY" button.
/// Used by both the signup flow and the baby scale setup flow.
struct BabyListStepView: View {
    @Environment(\.appTheme) private var theme
    @State private var openItemID: UUID?

    let title: String
    let addButtonText: String
    let babies: [BabyListItem]
    var onTapBaby: (Int) -> Void
    var onEditBaby: (Int) -> Void
    var onDeleteBaby: (Int) -> Void
    var onAddBaby: () -> Void

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .center, spacing: .spacingLG) {
                // Title
                Text(title)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, .spacingLG)

                // Baby list
                VStack(spacing: 0) {
                    ForEach(Array(babies.enumerated()), id: \.element.id) { index, baby in
                        UserListItemView(
                            user: UserItemInfo(
                                id: baby.id,
                                accountID: baby.accountID,
                                name: baby.name,
                                email: "",
                                isSelected: false,
                                isExpired: false,
                                canShowSelection: false
                            ),
                            openItemID: $openItemID,
                            iconSize: 32,
                            swipeButtonWidth: 56,
                            onTap: { _, _ in
                                onTapBaby(index)
                            },
                            onEdit: { _ in
                                onEditBaby(index)
                            },
                            onDelete: { _ in
                                onDeleteBaby(index)
                            }
                        )
                        if index < babies.count - 1 {
                            Divider()
                        }
                    }
                }
                .background(theme.backgroundPrimary)
                .cornerRadius(.spacingXS)

                // Add a Baby button
                ButtonView(
                    text: addButtonText,
                    type: .outlinedPrimary,
                    size: .large,
                    isDisabled: false
                ) {
                    onAddBaby()
                }
                .padding(.top, .spacingSM)
            }
            // .padding(.horizontal, .spacingMD)
        }
    }
}

#Preview {
    BabyListStepView(
        title: "Your baby has been added!",
        addButtonText: "ADD A BABY",
        babies: [],
        onTapBaby: { _ in },
        onEditBaby: { _ in },
        onDeleteBaby: { _ in },
        onAddBaby: {}
    )
    .padding()
}
