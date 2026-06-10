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
    /// Called when the user taps the edit icon or the note body in the expanded notes section.
    var onEditNotes: () -> Void = {}
    var openItemID: Binding<UUID?>?

    @State private var isNoteExpanded: Bool = false

    private var hasNotes: Bool { !(entry.notes ?? "").isEmpty }

    // Notes longer than ~2 lines (≈100 chars) get the "more" affordance.
    private var noteIsLong: Bool { (entry.notes ?? "").count > 100 }

    // MOB-458: use the shared relative → absolute formatter.
    // Entries on this screen are always same-day, so getArrivalRelativeTime returns
    // "Just now" / "X min ago" for recent readings or "9:52 AM" for older ones.
    private var timeText: String {
        DateTimeTools.getArrivalRelativeTime(fromISOString: entry.entryTimestamp)
            ?? DateTimeTools.getFormattedTime(entry.entryTimestamp)
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
                    Text(entry.weightDisplay)
                        .fontOpenSans(.heading5)
                        .foregroundColor(isExpanded ? theme.textInverse : theme.textHeading)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)

                    Text(HistoryListStrings.weight)
                        .fontOpenSans(.body3)
                        .foregroundColor(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Length — "--" when no length recorded (handled in weightDisplay/lengthDisplay)
                VStack(alignment: .leading, spacing: 2) {
                    Text(entry.lengthDisplay)
                        .fontOpenSans(.heading5)
                        .foregroundColor(isExpanded ? theme.textInverse : theme.textHeading)

                    Text(HistoryListStrings.length)
                        .fontOpenSans(.body3)
                        .foregroundColor(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Percentile
                VStack(alignment: .leading, spacing: 2) {
                    Text(BabyWeightPercentileCalculator.percentileDisplayText(entry.percentile))
                        .fontOpenSans(.heading5)
                        .foregroundColor(isExpanded ? theme.textInverse : theme.actionPrimary)

                    Text(HistoryListStrings.percentile)
                        .fontOpenSans(.body3)
                        .foregroundColor(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Expansion chevron
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

            // Expanded notes section
            if isExpanded {
                HStack(alignment: .top, spacing: .spacingXS) {
                    if hasNotes {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(entry.notes ?? "")
                                .fontOpenSans(.body3)
                                .foregroundColor(theme.textBody)
                                .lineLimit(isNoteExpanded ? nil : 2)
                                .onTapGesture { onEditNotes() }

                            if !isNoteExpanded && noteIsLong {
                                Button {
                                    withAnimation(.easeInOut(duration: 0.2)) {
                                        isNoteExpanded = true
                                    }
                                } label: {
                                    Text("more")
                                        .fontOpenSans(.body3)
                                        .foregroundColor(theme.actionPrimary)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    } else {
                        Text(HistoryListStrings.noNotesPlaceholder)
                            .fontOpenSans(.body3)
                            .foregroundColor(theme.textSubheading)
                            .onTapGesture { onEditNotes() }
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
        .onChange(of: isExpanded) { _, expanded in
            if !expanded { isNoteExpanded = false }
        }
    }
}
