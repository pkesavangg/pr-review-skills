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

    private var timeText: String {
        DateTimeTools.getFormattedTime(entry.entryTimestamp).lowercased()
    }

    private var weightText: String {
        "\(entry.weightLbs) \(HistoryListStrings.lbs) \(String(format: "%.1f", entry.weightOz)) \(HistoryListStrings.oz)"
    }

    private var lengthText: String {
        "\(Int(entry.lengthInches)) \(HistoryListStrings.inUnit)"
    }

    private var percentileText: String {
        "\(entry.percentile) \(HistoryListStrings.th)"
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
                        .fixedSize(horizontal: true, vertical: false)

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

                // Expansion chevron (hidden when no notes)
                AppIconView(icon: AppAssets.chevronDown)
                    .foregroundColor(isExpanded ? theme.actionInverse : theme.statusIconPrimary)
                    .rotationEffect(.degrees(isExpanded ? 180 : 0))
                    .opacity(entry.notes?.isEmpty == false ? 1 : 0)
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
            if let notes = entry.notes, !notes.isEmpty {
                onTap()
            }
        }
    }
}
