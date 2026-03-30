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
    private let swipeButtonWidth: CGFloat = 56

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
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
        }
    }

    @ViewBuilder
    private var babyListSection: some View {
        Section {
            ForEach(store.savedBabies, id: \.id) { baby in
                babyRow(baby)
                    .listRowInsets(top: 0, bottom: 0, leading: 0, trailing: 0)
            }
        } header: {
            Text(lang.title)
                .fontOpenSans(.heading4)
                .fontWeight(.bold)
                .foregroundColor(theme.textHeading)
                .textCase(nil)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.bottom, .spacingSM)
        }
        .listRowBackground(theme.backgroundPrimary)
        .listRowSeparatorTint(theme.statusUtilityPrimary)
    }

    private var addBabyCTA: some View {
        VStack(alignment: .center, spacing: .spacingLG) {
            ButtonView(
                text: lang.addABaby,
                type: .outlinedPrimary,
                size: .large,
                isDisabled: false
            ) {
                store.addAnotherBaby()
            }
        }
        .frame(maxWidth: .infinity)
        .listRowInsets(top: 0, bottom: 0, leading: 0, trailing: 0)
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }

    private func babyRow(_ baby: Baby) -> some View {
        HStack(spacing: .spacingSM) {
            let firstInitial = baby.name.firstAlphabeticCharacter().uppercased()
            InitialIconView(
                character: firstInitial,
                textColor: theme.backgroundPrimary,
                backgroundColor: theme.statusIconPrimary,
                size: 32,
                style: .fill
            )

            Text(baby.name)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)

            Spacer()

            Button {
                store.editBaby(baby)
            } label: {
                Image(systemName: "square.and.pencil")
                    .font(.system(size: 20))
                    .foregroundColor(theme.statusIconPrimary)
            }
        }
        .padding(.spacingSM)
        .background(theme.backgroundPrimary)
        .frame(height: 72)
        .swipeableActions(
            buttonWidth: swipeButtonWidth,
            buttons: [
                SwipeButton(
                    tint: theme.textError,
                    action: { store.deleteBabyFromList(baby) },
                    label: {
                        AnyView(
                            AppIconView(icon: AppAssets.trash, size: IconSize(width: 24, height: 24))
                                .foregroundColor(theme.backgroundPrimary)
                        )
                    }
                )
            ],
            itemID: UUID(uuidString: baby.id) ?? UUID(),
            openItemID: $openItemID,
            openThresholdFraction: 0.1,
            closeWithoutAnimationOnAction: true
        )
    }
}
