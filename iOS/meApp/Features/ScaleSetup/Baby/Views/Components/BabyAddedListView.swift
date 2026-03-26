//
//  BabyAddedListView.swift
//  meApp
//

import SwiftUI

/// "Your Baby Has Been Added!" — shows list of added babies with option to add more.
struct BabyAddedListView: View {
    @EnvironmentObject var store: BabyScaleSetupStore
    @Environment(\.appTheme) private var theme
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
                        HStack(spacing: .spacingSM) {
                            Image(systemName: "person.circle.fill")
                                .resizable()
                                .scaledToFit()
                                .frame(width: 32, height: 32)
                                .foregroundColor(theme.statusIconPrimary)

                            Text(baby.name)
                                .fontOpenSans(.body1)
                                .foregroundColor(theme.textBody)

                            Spacer()

                            // Edit button
                            Button {
                                // TODO: Edit baby profile
                            } label: {
                                Image(systemName: "square.and.pencil")
                                    .foregroundColor(theme.statusIconPrimary)
                            }

                            // Delete button
                            Button {
                                store.deleteBabyFromList(baby)
                            } label: {
                                Image(systemName: "trash.fill")
                                    .foregroundColor(theme.textError)
                            }
                            .padding(.leading, .spacingXS)
                        }
                        .padding(.horizontal, .spacingSM)
                        .padding(.vertical, .spacingXS)

                        Divider()
                    }
                }

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
