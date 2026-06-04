//
//  BabyHistoryEntryItem.swift
//  meApp
//

import SwiftUI

/// Displays a single baby history entry with expandable notes.
struct BabyHistoryEntryItem: View {
    @Environment(\.appTheme) private var theme

    let entry: BabyHistoryEntry
    let isExpanded: Bool
    let onTap: () -> Void
    let onDelete: () -> Void
    /// Called when the user taps the edit icon in the expanded notes section.
    var onEditNotes: () -> Void = {}
    var openItemID: Binding<UUID?>?

    private var hasNotes: Bool { !(entry.notes ?? "").isEmpty }

    private var timeText: String {
        DateTimeTools.getFormattedTime(entry.entryTimestamp).lowercased()
    }

    private var weightText: String {
        entry.weightDisplay
    }

    private var lengthText: String {
        entry.lengthDisplay
    }

    private var percentileText: String {
        BabyWeightPercentileCalculator.percentileDisplayText(entry.percentile)
    }

    var body: some View {
        VStack(spacing: 0) {
            // Main entry row
            HStack {
                // Time
                VStack(alignment: .leading, spacing: 2) {
                    Text(timeText)
                        .fontOpenSans(.heading5)
                        .foregroundColor(isExpanded ? theme.textInverse : theme.textHeading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Weight
                VStack(alignment: .leading, spacing: 2) {
                    Text(weightText)
                        .fontOpenSans(.heading5)
                        .foregroundColor(isExpanded ? theme.textInverse : theme.textHeading)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)

                    Text(HistoryListStrings.weight)
                        .fontOpenSans(.body3)
                        .foregroundColor(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Length
                VStack(alignment: .leading, spacing: 2) {
                    Text(lengthText)
                        .fontOpenSans(.heading5)
                        .foregroundColor(isExpanded ? theme.textInverse : theme.textHeading)

                    Text(HistoryListStrings.length)
                        .fontOpenSans(.body3)
                        .foregroundColor(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Percentile
                VStack(alignment: .leading, spacing: 2) {
                    Text(percentileText)
                        .fontOpenSans(.heading5)
                        .foregroundColor(isExpanded ? theme.textInverse : theme.actionPrimary)

                    Text(HistoryListStrings.percentile)
                        .fontOpenSans(.body3)
                        .foregroundColor(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Expansion chevron — always visible (row is always expandable)
                AppIconView(icon: AppAssets.chevronDown)
                    .foregroundColor(isExpanded ? theme.actionInverse : theme.statusIconPrimary)
                    .rotationEffect(.degrees(isExpanded ? 180 : 0))
            }
            .padding(.vertical, .spacingSM)
            .padding(.horizontal, .spacingSM)
            .contentShape(Rectangle())
            .background(isExpanded ? theme.actionSecondary : Color.clear)
            .swipeableActions(
                buttons: [
                    SwipeButton(
                        tint: theme.textError,
                        action: { onDelete() },
                        label: {
                            AnyView(
                                Text(CommonStrings.delete.uppercased())
                                    .fontOpenSans(.button1)
                                    .fontWeight(.bold)
                                    .foregroundColor(theme.textInverse)
                            )
                        }
                    )
                ],
                itemID: entry.id,
                openItemID: openItemID
            )

            Divider()
                .foregroundColor(theme.actionPrimary)

            // Expanded notes section — always shown when expanded
            if isExpanded {
                HStack(alignment: .top, spacing: .spacingXS) {
                    if hasNotes {
                        Text(entry.notes ?? "")
                            .fontOpenSans(.body3)
                            .foregroundColor(theme.textBody)
                    } else {
                        Text(HistoryListStrings.noNotesPlaceholder)
                            .fontOpenSans(.body3)
                            .foregroundColor(theme.textSubheading)
                    }
                    Spacer()
                    Button(action: onEditNotes) {
                        Image(systemName: "square.and.pencil")
                            .font(.system(size: 18))
                            .foregroundColor(theme.actionPrimary)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.spacingSM)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(theme.backgroundSecondary)

                Divider()
                    .foregroundColor(theme.actionPrimary)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: isExpanded)
        .contentShape(Rectangle())
        .onTapGesture {
            onTap()
        }
    }
}
