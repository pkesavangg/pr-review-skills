//
//  BabyAddedListView.swift
//  meApp
//

import SwiftUI

/// "Your Baby Has Been Added!" — shows list of added babies with option to add more.
struct BabyAddedListView: View {
    @EnvironmentObject var store: BabyScaleSetupStore
    @Environment(\.appTheme) private var theme
    @State private var openItemID: UUID?
    private let lang = BabyScaleSetupStrings.BabyAdded.self

    var body: some View {
        ScrollView {
            VStack(spacing: .spacingLG) {
                // Header
                Text(lang.title)
                    .fontOpenSans(.heading4)
                    .fontWeight(.bold)
                    .foregroundColor(theme.textHeading)
                    .multilineTextAlignment(.center)

                // Baby list
                VStack(spacing: 0) {
                    ForEach(store.savedBabies, id: \.id) { baby in
                        UserListItemView(
                            user: UserItemInfo(
                                accountID: baby.id,
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
                                store.editBaby(baby)
                            },
                            onEdit: { _ in
                                store.editBaby(baby)
                            },
                            onDelete: { _ in
                                store.confirmDeleteBabyFromList(baby)
                            }
                        )
                        if baby.id != store.savedBabies.last?.id {
                            Divider()
                        }
                    }
                }
                .background(theme.backgroundPrimary)
                .cornerRadius(.spacingXS)

                // Add a Baby button
                ButtonView(
                    text: lang.addABaby,
                    type: .outlinedPrimary,
                    size: .large,
                    isDisabled: false
                ) {
                    store.addAnotherBaby()
                }
                .padding(.horizontal, .spacingSM)
            }
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(.top, .spacingLG)
        }
    }
}
