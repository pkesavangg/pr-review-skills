//
//  BabyListStepView.swift
//  meApp
//

import SwiftUI

struct BabyListStepView: View {
    @ObservedObject var signupStore: SignupStore
    @Environment(\.appTheme) private var theme
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
                        BabyListRow(
                            baby: baby,
                            onEdit: { signupStore.editBaby(at: index) },
                            onDelete: { signupStore.deleteBaby(at: index) }
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

// MARK: - BabyListRow

private struct BabyListRow: View {
    @Environment(\.appTheme) private var theme
    let baby: SignupBaby
    let onEdit: () -> Void
    let onDelete: () -> Void

    var body: some View {
        HStack(spacing: .spacingSM) {
            // Initial circle
            Text(initial)
                .fontOpenSans(.heading5)
                .foregroundColor(theme.actionPrimary)
                .frame(width: 32, height: 32)
                .overlay(
                    Circle()
                        .stroke(theme.actionPrimary, lineWidth: 2)
                )

            // Name
            Text(baby.name)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)

            Spacer()

            // Edit button
            Button(action: onEdit) {
                Image(systemName: "square.and.pencil")
                    .foregroundColor(theme.textBody)
            }
            .buttonStyle(.plain)

            // Delete button
            Button(action: onDelete) {
                Image(systemName: "trash")
                    .foregroundColor(.red)
            }
            .buttonStyle(.plain)
        }
        .padding(.spacingSM)
    }

    private var initial: String {
        String(baby.name.prefix(1)).uppercased()
    }
}

#Preview {
    BabyListStepView(signupStore: SignupStore())
        .padding()
}
