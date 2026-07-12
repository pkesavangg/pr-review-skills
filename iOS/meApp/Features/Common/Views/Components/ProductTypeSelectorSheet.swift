//
//  ProductTypeSelectorSheet.swift
//  meApp
//

import SwiftUI

/// Full sheet that shows the list of selectable product types / baby profiles.
/// Presented when the user taps the header dropdown trigger.
/// Follows the same pattern as MyAccountsScreen (list with checkmarks).
struct ProductTypeSelectorSheet: View {
    @Environment(\.appTheme) private var theme
    @ObservedObject var store: ProductTypeStore
    @Binding var isPresented: Bool
    let title: String

    var body: some View {
        VStack(spacing: 0) {
            // Header
            NavbarHeaderView<AnyView, EmptyView>(
                title: title,
                leadingContent: {
                    AnyView(
                        AppIconView(icon: AppAssets.close)
                    )
                },
                onLeadingTap: {
                    isPresented = false
                },
                canShowBorder: true
            )

            // Item list — same pattern as MyAccountsScreen
            ZStack {
                theme.backgroundSecondary.ignoresSafeArea()
                List {
                    Section {
                        ForEach(store.availableItems, id: \.id) { item in
                            ProductTypeSelectorRow(
                                item: item,
                                isSelected: store.selectedItem == item
                            ) {
                                store.select(item)
                                isPresented = false
                            }
                            .listRowInsets(top: 0, bottom: 0, leading: 0, trailing: 0)
                        }
                    }
                    .listRowBackground(theme.backgroundPrimary)
                    .listRowSeparatorTint(theme.statusUtilityPrimary)
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
            }
        }
    }
}

/// A single row in the selector sheet.
/// Matches the UserListItemView checkmark pattern from MyAccountsScreen.
struct ProductTypeSelectorRow: View {
    @Environment(\.appTheme) private var theme
    let item: ProductSelection
    let isSelected: Bool
    let onTap: () -> Void

    /// Per-product accent color applied to the row label (per Figma):
    /// weight → blue, blood pressure → green, baby → purple.
    private var accentColor: Color {
        theme.productAccentColor(for: item.entryType)
    }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: .spacingSM) {
                Text(item.displayName)
                    .fontOpenSans(.body2)
                    .foregroundColor(accentColor)
                Spacer()
                AppIconView(
                    icon: isSelected ? AppAssets.circleCheckFilled : AppAssets.circleOutline,
                    size: IconSize(width: 24, height: 24)
                )
                .foregroundColor(theme.statusIconPrimary)
            }
            .padding(.horizontal, .spacingSM)
            .frame(height: 56)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
