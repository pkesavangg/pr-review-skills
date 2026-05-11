//
//  BabyDaySummaryItem.swift
//  meApp
//

import SwiftUI

/// Reusable list-row that shows a single day summary for baby history.
struct BabyDaySummaryItem: View {
    @Environment(\.appTheme) private var theme

    let day: BabyHistoryDay

    private var dateText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        guard let date = formatter.date(from: day.id) else { return day.id }
        formatter.dateFormat = "M/d/yy"
        return formatter.string(from: date)
    }

    private var weightText: String {
        day.weightDisplay
    }

    private var lengthText: String {
        day.lengthDisplay
    }

    private var percentileText: String {
        BabyWeightPercentileCalculator.percentileDisplayText(day.percentile)
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                // Date & entry count
                VStack(alignment: .leading) {
                    Text(dateText)
                        .fontOpenSans(.heading5)
                        .foregroundColor(theme.textHeading)

                    Text("\(day.entryCount) \(HistoryListStrings.entries)")
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Weight
                VStack(alignment: .leading) {
                    Text(weightText)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)

                    Text(HistoryListStrings.weight)
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.textSubheading)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Length
                VStack(alignment: .leading) {
                    Text(lengthText)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)

                    Text(HistoryListStrings.length)
                        .fontOpenSans(.body3)
                        .foregroundColor(theme.textSubheading)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Percentile
                VStack(alignment: .leading) {
                    Text(percentileText)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.actionPrimary)

                    Text(HistoryListStrings.percentile)
                        .fontOpenSans(.body3)
                        .foregroundColor(theme.textSubheading)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Chevron icon
                AppIconView(icon: AppAssets.chevronRight, size: IconSize(
                    width: 32, height: 32
                ))
                    .foregroundColor(theme.statusIconPrimary)
            }
            .padding(.vertical, .spacingMD)
            .padding(.horizontal, .spacingSM)
            Divider()
                .foregroundColor(theme.actionPrimary)
        }
    }
}

/// Section header for a baby history week group.
struct BabyWeekHeaderView: View {
    @Environment(\.appTheme) private var theme

    let weekNumber: Int

    var body: some View {
        HStack {
            Text("\(HistoryListStrings.week) \(weekNumber)")
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
            Spacer()
        }
        .padding(.horizontal, .spacingSM)
        .padding(.top, .spacingMD)
        .padding(.bottom, .spacingXS)
    }
}
