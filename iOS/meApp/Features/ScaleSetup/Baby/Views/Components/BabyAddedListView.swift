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

                // Baby list with swipe-to-delete
                VStack(spacing: 0) {
                    ForEach(store.savedBabies, id: \.id) { baby in
                        babyRow(baby)
                    }
                }
                .padding(.horizontal, .spacingSM)

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

    private func babyRow(_ baby: Baby) -> some View {
        HStack(spacing: .spacingSM) {
            Image(systemName: "person.circle.fill")
                .resizable()
                .scaledToFit()
                .frame(width: 32, height: 32)
                .foregroundColor(theme.statusIconPrimary)

            Text(baby.name)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)

            Spacer()

            // Edit button
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
