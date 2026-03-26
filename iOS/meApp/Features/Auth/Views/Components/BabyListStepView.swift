//
//  BabyListStepView.swift
//  meApp
//

import SwiftUI

struct BabyListStepView: View {
    @ObservedObject var signupStore: SignupStore
    @Environment(\.appTheme) private var theme
    @State private var openItemID: UUID?
    let lang = SignupStrings.BabyListStep.self

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .center, spacing: .spacingLG) {
                // Title
                Text(lang.title)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                    .frame(maxWidth: .infinity, alignment: .leading)

                // Baby list
                VStack(spacing: 0) {
                    ForEach(Array(signupStore.babies.enumerated()), id: \.element.id) { index, baby in
                        UserListItemView(
                            user: UserItemInfo(
                                id: baby.id,
                                accountID: baby.id.uuidString,
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
                                signupStore.editBaby(at: index)
                            },
                            onEdit: { _ in
                                signupStore.editBaby(at: index)
                            },
                            onDelete: { _ in
                                signupStore.confirmDeleteBaby(at: index)
                            }
                        )
                        if index < signupStore.babies.count - 1 {
                            Divider()
                        }
                    }
                }
                .background(theme.backgroundPrimary)
                .cornerRadius(.spacingXS)

                // Add a Baby button
                ButtonView(
                    text: lang.addBabyButton,
                    type: .filledPrimary,
                    size: .small,
                    isDisabled: false
                ) {
                    signupStore.addAnotherBaby()
                }
            }
        }
    }
}

#Preview {
    BabyListStepView(signupStore: SignupStore())
        .padding()
}
