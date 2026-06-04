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
                // Timestamp — relative when recent, absolute otherwise (MOB-458)
                Text(DateTimeTools.getArrivalRelativeTime(fromISOString: entry.entryTimestamp)
                    ?? DateTimeTools.getFormattedDay(entry.entryTimestamp))
                    .fontOpenSans(.heading5)
                    .foregroundColor(isExpanded ? theme.textInverse : theme.textHeading)
                    .frame(maxWidth: .infinity, alignment: .leading)

                // Pressure value
                VStack(alignment: .leading, spacing: 2) {
                    Text(pressureText)
                        .fontOpenSans(.heading5)
                        .foregroundColor(pressureColor)

                    Text(HistoryListStrings.mmhg)
                        .fontOpenSans(.body3)
                        .foregroundColor(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Pulse value
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(entry.pulse)")
                        .fontOpenSans(.heading5)
                        .foregroundColor(isExpanded ? theme.textInverse : theme.textHeading)

                    Text(HistoryListStrings.pulse)
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
