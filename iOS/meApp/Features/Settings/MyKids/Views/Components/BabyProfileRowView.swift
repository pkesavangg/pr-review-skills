//
//  BabyProfileRowView.swift
//  meApp
//

import SwiftUI

/// A single baby entry in the "My Kids" list (MOB-1605).
///
/// Collapsed, it shows a circular avatar initial, the baby's name, an edit control and an
/// expand chevron. Expanded, it reveals the baby's profile details (birthday, biological sex,
/// birth length, birth weight). Tapping the header (or the chevron) toggles the details;
/// tapping the edit icon opens the edit-baby flow.
///
/// Swipe-to-delete is applied by the parent (`MyKidsScreen`) so the swipe state machine can
/// coordinate one open row at a time.
struct BabyProfileRowView: View {
    @Environment(\.appTheme) private var theme

    let name: String
    let details: [BabyProfileDetail]
    let isExpanded: Bool
    let babyId: String
    /// Stable swipe identity + shared "one open row" binding, owned by the parent.
    let swipeItemID: UUID
    @Binding var openSwipeItemID: UUID?
    let onToggleExpand: () -> Void
    let onEdit: () -> Void
    let onDelete: () -> Void

    private let avatarSize: CGFloat = 32
    private let headerHeight: CGFloat = 72
    private let swipeButtonWidth: CGFloat = 56

    var body: some View {
        VStack(spacing: 0) {
            // Swipe-to-delete wraps ONLY the header (the profile item), so revealing the
            // trash never spans the expanded detail section — it stays aligned to the
            // profile row like before the detail section existed (MOB-1605). The trailing
            // corners round only when collapsed (the header is then the whole card).
            header
                .swipeableActions(
                    buttonWidth: swipeButtonWidth,
                    buttons: [deleteSwipeButton],
                    itemID: swipeItemID,
                    openItemID: $openSwipeItemID,
                    openThresholdFraction: 0.1,
                    closeWithoutAnimationOnAction: true,
                    trailingCornerRadius: isExpanded ? 0 : .radiusSM
                )

            // Expanded details fade in/out with the same style as the History entry detail:
            // `.transition(.opacity)` on the section + a single `.animation(value:)` on the
            // outer VStack. The header stays fixed and never overlaps because the swipe wraps
            // ONLY the header, so the height animation isn't nested inside the swipe's own
            // animation (that nesting is what jerked earlier) (MOB-1605).
            if isExpanded {
                VStack(spacing: 0) {
                    Divider()
                        .overlay(theme.statusUtilityPrimary)
                        .padding(.horizontal, .spacingSM)
                    detailSection
                }
                .transition(.opacity)
            }
        }
        .background(theme.backgroundPrimary)
        .animation(.easeInOut(duration: 0.25), value: isExpanded)
    }

    /// Trash action revealed by the header swipe.
    private var deleteSwipeButton: SwipeButton {
        SwipeButton(
            tint: theme.textError,
            action: { onDelete() },
            label: {
                AnyView(
                    AppIconView(icon: AppAssets.trash, size: IconSize(width: 24, height: 24))
                        .foregroundColor(theme.backgroundPrimary)
                        // Icon-only: names the VoiceOver custom action too
                        .accessibilityLabel(CommonStrings.delete)
                )
            }
        )
    }

    // MARK: - Header

    private var header: some View {
        HStack(spacing: .spacingSM) {
            InitialIconView(
                character: name.firstAlphabeticCharacter().uppercased(),
                textColor: theme.backgroundPrimary,
                backgroundColor: theme.statusIconPrimary,
                size: avatarSize,
                style: .fill
            )
            .accessibilityHidden(true)

            Text(name)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)

            Spacer()

            Button(action: onEdit) {
                Image(systemName: "square.and.pencil")
                    .font(.system(size: 20))
                    .foregroundColor(theme.statusIconPrimary)
                    .frame(minWidth: 44, minHeight: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel(CommonStrings.edit)
            .appAccessibility(id: AccessibilityID.myKidsEditBabyButton + "_" + babyId)

            Button(action: onToggleExpand) {
                AppIconView(icon: AppAssets.chevronDown)
                    .foregroundColor(theme.statusIconPrimary)
                    .rotationEffect(.degrees(isExpanded ? 180 : 0))
                    .frame(minWidth: 44, minHeight: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel(isExpanded
                ? MyKidsStrings.Details.collapseAccessibilityLabel
                : MyKidsStrings.Details.expandAccessibilityLabel)
            .appAccessibility(id: AccessibilityID.myKidsExpandBabyButton + "_" + babyId)
        }
        .padding(.spacingSM)
        .frame(height: headerHeight)
        .frame(maxWidth: .infinity)
        // Opaque so the header occludes the red delete panel behind it while swiping.
        .background(theme.backgroundPrimary)
        .contentShape(Rectangle())
        .onTapGesture(perform: onToggleExpand)
    }

    // MARK: - Expanded Details

    private var detailSection: some View {
        VStack(spacing: 0) {
            ForEach(Array(details.enumerated()), id: \.element.id) { index, detail in
                detailRow(detail)

                if index < details.count - 1 {
                    Divider()
                        .overlay(theme.statusUtilityPrimary)
                        .padding(.horizontal, .spacingSM)
                }
            }
        }
        .padding(.bottom, .spacingXS)
    }

    private func detailRow(_ detail: BabyProfileDetail) -> some View {
        HStack {
            Text(detail.label)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textSubheading)

            Spacer()

            Text(detail.value)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
        }
        .padding(.horizontal, .spacingSM)
        .padding(.vertical, .spacingSM)
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Preview

#Preview {
    let details = [
        BabyProfileDetail(label: MyKidsStrings.Details.birthday, value: "June 10, 2024"),
        BabyProfileDetail(label: MyKidsStrings.Details.biologicalSex, value: "Male"),
        BabyProfileDetail(label: MyKidsStrings.Details.birthLength, value: "25.8 in"),
        BabyProfileDetail(label: MyKidsStrings.Details.birthWeight, value: "16 lb 8 oz")
    ]
    return VStack(spacing: .spacingSM) {
        BabyProfileRowView(
            name: "Tammy Thompson",
            details: details,
            isExpanded: false,
            babyId: "preview-1",
            swipeItemID: UUID(),
            openSwipeItemID: .constant(nil),
            onToggleExpand: {},
            onEdit: {},
            onDelete: {}
        )
        .cornerRadius(.radiusSM)

        BabyProfileRowView(
            name: "Sally Thompson",
            details: details,
            isExpanded: true,
            babyId: "preview-2",
            swipeItemID: UUID(),
            openSwipeItemID: .constant(nil),
            onToggleExpand: {},
            onEdit: {},
            onDelete: {}
        )
        .cornerRadius(.radiusSM)
    }
    .padding(.spacingSM)
    .themeable()
}
