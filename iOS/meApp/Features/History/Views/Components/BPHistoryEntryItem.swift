//
//  BPHistoryEntryItem.swift
//  meApp
//

import SwiftUI

/// Displays a single blood pressure history entry with expandable notes.
struct BPHistoryEntryItem: View {
    @Environment(\.appTheme) private var theme

    let entry: BPHistoryEntry
    let isExpanded: Bool
    let onTap: () -> Void
    let onDelete: () -> Void
    /// Called when the user taps the edit icon in the expanded notes section.
    var onEditNotes: () -> Void = {}
    var openItemID: Binding<UUID?>?

    private var pressureText: String {
        "\(entry.systolic)/\(entry.diastolic)"
    }

    private var hasNotes: Bool { !(entry.notes ?? "").isEmpty }

    private var pressureColor: Color {
        BPCategory.classify(systolic: entry.systolic, diastolic: entry.diastolic).color(theme: theme)
    }

    var body: some View {
        VStack(spacing: 0) {
            // Main entry row
            HStack {
                // Date and time
                VStack(alignment: .leading, spacing: 2) {
                    Text(DateTimeTools.getFormattedDay(entry.entryTimestamp))
                        .fontOpenSans(.heading5)
                        .foregroundStyle(isExpanded ? theme.textInverse : theme.textHeading)

                    Text(DateTimeTools.getFormattedTime(entry.entryTimestamp).lowercased())
                        .fontOpenSans(.body3)
                        .foregroundStyle(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Pressure value
                VStack(alignment: .leading, spacing: 2) {
                    Text(pressureText)
                        .fontOpenSans(.heading5)
                        .foregroundStyle(pressureColor)

                    Text(HistoryListStrings.mmhg)
                        .fontOpenSans(.body3)
                        .foregroundStyle(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Pulse value
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(entry.pulse)")
                        .fontOpenSans(.heading5)
                        .foregroundStyle(isExpanded ? theme.textInverse : theme.textHeading)

                    Text(HistoryListStrings.pulse)
                        .fontOpenSans(.body3)
                        .foregroundStyle(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Expansion chevron — always visible (row is always expandable)
                AppIconView(icon: AppAssets.chevronDown)
                    .foregroundStyle(isExpanded ? theme.actionInverse : theme.statusIconPrimary)
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
                                    .foregroundStyle(theme.textInverse)
                            )
                        }
                    )
                ],
                itemID: entry.id,
                openItemID: openItemID
            )

            Divider()
                .foregroundStyle(theme.actionPrimary)

            // Expanded notes section — always shown when expanded
            if isExpanded {
                HStack(alignment: .top, spacing: .spacingXS) {
                    if hasNotes {
                        Text(entry.notes ?? "")
                            .fontOpenSans(.body3)
                            .foregroundStyle(theme.textBody)
                    } else {
                        Text(HistoryListStrings.noNotesPlaceholder)
                            .fontOpenSans(.body3)
                            .foregroundStyle(theme.textSubheading)
                    }
                    Spacer()
                    Button("Edit notes", systemImage: "square.and.pencil", action: onEditNotes)
                        .labelStyle(.iconOnly)
                        .font(.system(size: 18))
                        .foregroundStyle(theme.actionPrimary)
                }
                .padding(.spacingSM)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(theme.backgroundSecondary)

                Divider()
                    .foregroundStyle(theme.actionPrimary)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: isExpanded)
        .contentShape(Rectangle())
        .onTapGesture {
            onTap()
        }
    }
}
