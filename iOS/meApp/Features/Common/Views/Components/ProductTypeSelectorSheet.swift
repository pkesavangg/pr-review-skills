//
//  ProductTypeSelectorSheet.swift
//  meApp
//

import SwiftUI

/// Full sheet that shows the list of selectable product types / baby profiles.
/// Presented when the user taps the header dropdown trigger.
/// Follows the same pattern as MyAccountsScreen (list with checkmarks).
struct ProductTypeSelectorSheet: View {
    /// MOB-1726: brief hold so the tapped row's checkmark is visibly selected BEFORE the sheet dismisses.
    /// Without it, `select` + `isPresented = false` ran on the same frame, and the heavy product switch
    /// `select` triggers janked the main thread — so the tick never visibly moved and the sheet appeared to
    /// hang. We flip the tick via local `pendingSelection` immediately, then apply the real selection + close.
    private static let selectionConfirmationDelay: TimeInterval = 0.2

    @Environment(\.appTheme) private var theme
    @ObservedObject var store: ProductTypeStore
    @Binding var isPresented: Bool
    let title: String
    /// Locally-chosen row, shown as selected instantly on tap (before `store.select` runs). `nil` until a tap.
    @State private var pendingSelection: ProductSelection?

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
                canShowBorder: true,
                leadingAccessibilityID: AccessibilityID.productSelectorCloseButton
            )

            // Item list — same pattern as MyAccountsScreen
            ZStack {
                theme.backgroundSecondary.ignoresSafeArea()
                List {
                    Section {
                        ForEach(store.availableItems, id: \.id) { item in
                            ProductTypeSelectorRow(
                                item: item,
                                isSelected: (pendingSelection ?? store.selectedItem) == item
                            ) {
                                // MOB-1726: show the tick immediately via local state, then apply the real
                                // selection + dismiss after a brief beat so the switch's main-thread work
                                // doesn't swallow the checkmark update / stall the dismissal.
                                guard pendingSelection == nil else { return }
                                pendingSelection = item
                                // MOB-1726: apply the real selection + dismiss after a brief beat so the tick
                                // is visibly selected first. A cancellable Task keyed to `item` (not a fixed
                                // `asyncAfter`) skips the stale apply if the sheet was dismissed another way
                                // (the close button flips `isPresented`) during the beat.
                                Task { @MainActor in
                                    try? await Task.sleep(nanoseconds: UInt64(Self.selectionConfirmationDelay * 1_000_000_000))
                                    guard isPresented, pendingSelection == item else { return }
                                    store.select(item)
                                    isPresented = false
                                }
                            }
                            .listRowInsets(top: 0, bottom: 0, leading: 0, trailing: 0)
                            .accessibilityElement(children: .contain)
                            .appAccessibility(id: AccessibilityID.productSelectorRow + "_" + item.id)
                        }
                    }
                    .listRowBackground(theme.backgroundPrimary)
                    .listRowSeparatorTint(theme.statusUtilityPrimary)
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
            }
        }
        .screenAccessibilityRoot(AccessibilityID.productSelectorScreenRoot)
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
