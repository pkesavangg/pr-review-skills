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

    private var timeText: String {
        DateTimeTools.getFormattedTime(entry.entryTimestamp)
    }

    private var combinedAccessibilityLabel: String {
        "\(timeText), \(HistoryListStrings.weight) \(entry.weightDisplay), \(HistoryListStrings.length) \(entry.lengthDisplay)"
    }

    var body: some View {
        VStack(spacing: 0) {
            // Main entry row
            HStack {
                // Time
                VStack(alignment: .leading, spacing: 2) {
                    Text(timeText)
                        .fontOpenSans(.heading5)
                        .foregroundStyle(isExpanded ? theme.textInverse : theme.textHeading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Weight
                VStack(alignment: .leading, spacing: 2) {
                    Text(entry.weightDisplay)
                        .fontOpenSans(.heading5)
                        .foregroundStyle(isExpanded ? theme.textInverse : theme.babyScaleColor)

                    Text(HistoryListStrings.weight)
                        .fontOpenSans(.body3)
                        .foregroundStyle(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Length — "--" when no length recorded (handled in weightDisplay/lengthDisplay)
                VStack(alignment: .leading, spacing: 2) {
                    Text(entry.lengthDisplay)
                        .fontOpenSans(.heading5)
                        .foregroundStyle(isExpanded ? theme.textInverse : theme.babyScaleColor)

                    Text(HistoryListStrings.length)
                        .fontOpenSans(.body3)
                        .foregroundStyle(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Percentile
                VStack(alignment: .leading, spacing: 2) {
                    Text(BabyWeightPercentileCalculator.percentileDisplayText(entry.percentile))
                        .fontOpenSans(.heading5)
                        .foregroundStyle(isExpanded ? theme.textInverse : theme.babyScaleColor)

                    Text(HistoryListStrings.percentile)
                        .fontOpenSans(.body3)
                        .foregroundStyle(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Expansion chevron
                AppIconView(icon: AppAssets.chevronDown)
                    .foregroundStyle(isExpanded ? theme.actionInverse : theme.statusIconPrimary)
                    .rotationEffect(.degrees(isExpanded ? 180 : 0))
            }
            .padding(.vertical, .spacingSM)
            .padding(.horizontal, .spacingSM)
            .contentShape(Rectangle())
            // Expanded row is a dark highlight: its values use inverse (light) text, so the
            // background must be the dark actionPrimary. actionSecondary is the same light
            // token as textInverse, which made the values invisible.
            .background(isExpanded ? theme.actionPrimary : Color.clear)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(combinedAccessibilityLabel)
            .accessibilityAddTraits(.isButton)
            .accessibilityHint(isExpanded ? HistoryListStrings.accEntryCollapseHint : HistoryListStrings.accEntryExpandHint)
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
                                    .accessibilityLabel(HistoryListStrings.accDeleteEntryLabel)
                                    .accessibilityIdentifier(AccessibilityID.historyDeleteButton)
                            )
                        }
                    )
                ],
                itemID: entry.id,
                openItemID: openItemID
            )

            Divider()
                .foregroundStyle(theme.actionPrimary)

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
                        // "+" to add when no note exists, boxed pencil to edit once it does.
                        Image(systemName: hasNotes ? "square.and.pencil" : "plus")
                            .font(.system(size: 18))
                            .foregroundColor(theme.actionPrimary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(hasNotes ? HistoryListStrings.accEditNoteLabel : HistoryListStrings.accAddNoteLabel)
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
        .onChange(of: isExpanded) { _, expanded in
            if !expanded { isNoteExpanded = false }
        }
    }
}
