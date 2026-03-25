///
///  A3BpmUserSelectionView.swift
///  meApp
///

import SwiftUI

/// User selection view for the ``BpmSetupStep/selectUser`` step.
/// Uses the static A3 BPM user icons on the "Which user do you want to be?" screen.
struct A3BpmUserSelectionView: View {
    @Environment(\.appTheme) private var theme

    let sku: String
    let selectedUser: Int?
    let onSelect: (Int) -> Void

    private let lang = BpmSetupStrings.SelectUser.self

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: .spacingLG) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(lang.title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .multilineTextAlignment(.leading)
                        .lineLimit(nil)

                    Text(lang.description)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                        .multilineTextAlignment(.leading)
                        .lineLimit(nil)
                }
                .padding(.horizontal, .spacingXSM)

                HStack(spacing: .spacingLG) {
                    BpmUserIconButton(
                        iconName: firstUserIcon,
                        isSelected: selectedUser == 1
                    ) {
                        onSelect(1)
                    }

                    BpmUserIconButton(
                        iconName: secondUserIcon,
                        isSelected: selectedUser == 2
                    ) {
                        onSelect(2)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .center)
            }
            .padding(.top, .spacingLG)
        }
    }

    private var firstUserIcon: String {
        AppAssets.a3BpmUser1
    }

    private var secondUserIcon: String {
        AppAssets.a3BpmUser2
    }
}

private struct BpmUserIconButton: View {
    let iconName: String
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Image(iconName)
                .resizable()
                .scaledToFit()
                .frame(width: 89, height: 161)
                .opacity(isSelected ? 1.0 : 0.72)
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    A3BpmUserSelectionView(sku: "0603", selectedUser: 1, onSelect: { _ in })
        .padding(.horizontal)
        .environmentObject(Theme.shared)
}
