///
///  A3BpmUserSelectionView.swift
///  meApp
///

import SwiftUI

/// User selection view for the ``BpmSetupStep/selectUser`` step.
/// Renders device-specific user slot icons based on the SKU.
struct A3BpmUserSelectionView: View {
    @Environment(\.appTheme) private var theme

    let sku: String
    @Binding var selectedUser: Int?
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

                if a6BpmSkus.contains(sku) {
                    HStack(spacing: .spacingLG) {
                        BpmUserIconButton(
                            iconName: AppAssets.a6BpmUserA,
                            isSelected: selectedUser == 1
                        ) {
                            onSelect(1)
                        }

                        BpmUserIconButton(
                            iconName: AppAssets.a6BpmUserB,
                            isSelected: selectedUser == 2
                        ) {
                            onSelect(2)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .center)
                } else {
                    HStack(spacing: .spacingLG) {
                        BpmUserIconButton(
                            iconName: AppAssets.a3BpmUser1,
                            isSelected: selectedUser == 1
                        ) {
                            onSelect(1)
                        }

                        BpmUserIconButton(
                            iconName: AppAssets.a3BpmUser2,
                            isSelected: selectedUser == 2
                        ) {
                            onSelect(2)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .center)
                }
            }
            .padding(.top, .spacingLG)
        }
    }
}

// MARK: - Catalog icon button

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
    A3BpmUserSelectionView(sku: "0603", selectedUser: .constant(1), onSelect: { _ in })
        .padding(.horizontal)
        .environmentObject(Theme.shared)
}
