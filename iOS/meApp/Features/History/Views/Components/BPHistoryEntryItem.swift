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

    private var pressureText: String {
        "\(entry.systolic)/\(entry.diastolic)"
    }

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
                        .foregroundColor(isExpanded ? theme.textInverse : theme.textHeading)

                    Text(DateTimeTools.getFormattedTime(entry.entryTimestamp).lowercased())
                        .fontOpenSans(.body3)
                        .foregroundColor(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Pressure value
                VStack(spacing: 2) {
                    Text(pressureText)
                        .fontOpenSans(.heading3)
                        .foregroundColor(pressureColor)

                    Text(HistoryListStrings.mmhg)
                        .fontOpenSans(.body3)
                        .foregroundColor(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                .frame(maxWidth: .infinity)

                // Pulse value
                VStack(spacing: 2) {
                    Text("\(entry.pulse)")
                        .fontOpenSans(.heading3)
                        .foregroundColor(isExpanded ? theme.textInverse : theme.textHeading)

                    Text(HistoryListStrings.pulse)
                        .fontOpenSans(.body3)
                        .foregroundColor(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                .frame(maxWidth: .infinity)

                // Expansion chevron (only if notes exist)
                if entry.notes != nil {
                    AppIconView(icon: AppAssets.chevronDown)
                        .foregroundColor(isExpanded ? theme.actionInverse : theme.statusIconPrimary)
                        .rotationEffect(.degrees(isExpanded ? 180 : 0))
                }
            }
            .padding(.vertical, .spacingSM)
            .padding(.horizontal, .spacingSM)
            .contentShape(Rectangle())
            .background(isExpanded ? theme.actionSecondary : Color.clear)

            Divider()
                .foregroundColor(theme.actionPrimary)

            // Expanded notes section
            if isExpanded, let notes = entry.notes {
                VStack(alignment: .leading, spacing: 0) {
                    Text(notes)
                        .fontOpenSans(.body3)
                        .foregroundColor(theme.textBody)
                        .padding(.spacingSM)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(theme.backgroundSecondary)

                Divider()
                    .foregroundColor(theme.actionPrimary)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: isExpanded)
        .contentShape(Rectangle())
        .onTapGesture {
            guard entry.notes != nil else { return }
            onTap()
        }
    }
}
