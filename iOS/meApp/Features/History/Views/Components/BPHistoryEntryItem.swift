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
        AhaPressureClass.classify(systolic: entry.systolic, diastolic: entry.diastolic).color(theme: theme)
    }

    private var combinedAccessibilityLabel: String {
        let day = DateTimeTools.getFormattedDay(entry.entryTimestamp)
        let time = DateTimeTools.getFormattedTime(entry.entryTimestamp)
        return "\(day), \(time), \(entry.systolic) over \(entry.diastolic) \(HistoryListStrings.mmhg), \(HistoryListStrings.pulse) \(entry.pulse)"
    }

    var body: some View {
        VStack(spacing: 0) {
            // Main entry row
            HStack {
                // Date + time shown on two lines to match the design
                VStack(alignment: .leading, spacing: 2) {
                    Text(DateTimeTools.getFormattedDay(entry.entryTimestamp))
                        .fontOpenSans(.heading5)
                        .foregroundColor(isExpanded ? theme.textInverse : theme.textHeading)
                    Text(DateTimeTools.getFormattedTimeLowercased(entry.entryTimestamp))
                        .fontOpenSans(.body3)
                        .foregroundStyle(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Pressure value
                VStack(alignment: .leading, spacing: 2) {
                    Text(pressureText)
                        .fontOpenSans(.heading5)
                        .foregroundStyle(pressureColor)

                    Text(EntryUnit.mmhg.displayString)
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
            // Expanded row is a dark highlight: its values use inverse (light) text, so the
            // background must be the dark actionPrimary. actionSecondary is the same light
            // token as textInverse, which made the values invisible.
            .background(isExpanded ? theme.actionPrimary : Color.clear)
            .appAccessibility(id: AccessibilityID.historyBPRowExpand)
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

            // Expanded notes section — always shown when expanded
            if isExpanded {
                HStack(alignment: .center, spacing: .spacingXS) {
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
                    Button(action: onEditNotes) {
                        // "+" to add when no note exists, boxed pencil to edit once it does.
                        Image(systemName: hasNotes ? "square.and.pencil" : "plus")
                            .font(.system(size: 18))
                            .foregroundStyle(theme.actionPrimary)
                            // Guarantee at least a 44×44pt tap target (Apple HIG) — the glyph
                            // stays visually 18pt but the whole square is tappable.
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
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
    }
}
