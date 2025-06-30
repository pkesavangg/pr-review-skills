//  IntegrationListItemView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 26/06/25.
//
import SwiftUI

// MARK: - IntegrationListItemView
/// A single row inside the integrations list showing provider logo, title and selection indicator.
struct IntegrationListItemView: View {
    @Environment(\.appTheme) private var theme

    let item: IntegrationItem
    var onTap: () -> Void
    let rowHeight: CGFloat = 64
    var body: some View {
        VStack {
            Spacer()
            HStack(spacing: .spacingSM) {
                Image(item.type.iconAsset)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 42, height: 44)

                Text(item.type.displayName)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)

                Spacer()

                AppIconView(
                    icon: item.isSelected ? AppAssets.circleCheckFilled : AppAssets.circleOutline,
                    size: IconSize(width: 24, height: 24)
                )
                .foregroundColor(theme.statusIconPrimary)
            }
            Spacer()
        }
        .frame(height: rowHeight)
        .contentShape(Rectangle())
        .onTapGesture { onTap() }
    }
}

// MARK: - Preview
#Preview {
    List {
        Section {
            IntegrationListItemView(
                item: IntegrationItem(
                    type: .appleHealth,
                    isSelected: true
                ),
                onTap: {}
            )
            .listRowInsets()
            IntegrationListItemView(
                item: IntegrationItem(
                    type: .myFitnessPal,
                    isSelected: false
                ),
                onTap: {}
            )
            .listRowInsets()
            IntegrationListItemView(
                item: IntegrationItem(
                    type: .fitbit,
                    isSelected: false
                ),
                onTap: {}
            )
            .listRowInsets()
        }
    }
    .listStyle(.insetGrouped)
    .scrollContentBackground(.hidden)
    .environmentObject(Theme.shared)
    .background(.gray.opacity(0.51))
}
