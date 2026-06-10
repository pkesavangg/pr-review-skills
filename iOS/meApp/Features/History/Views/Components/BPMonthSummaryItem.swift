//
//  BPMonthSummaryItem.swift
//  meApp
//

import SwiftUI

/// Reusable list-row that shows a single month summary for blood pressure history.
struct BPMonthSummaryItem: View {
    @Environment(\.appTheme) private var theme

    let month: BPHistoryMonth

    private var monthYearText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM"
        guard let date = formatter.date(from: month.id) else { return month.id }
        formatter.dateFormat = "MMM yyyy"
        return formatter.string(from: date)
    }

    private var pressureColor: Color {
        BPCategory.classify(systolic: month.avgSystolic, diastolic: month.avgDiastolic).color(theme: theme)
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                // Month & entry count
                VStack(alignment: .leading) {
                    Text(monthYearText)
                        .fontOpenSans(.heading5)
                        .foregroundColor(theme.textHeading)

                    Text("\(month.count) \(HistoryListStrings.entries)")
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Average pressure
                VStack(alignment: .leading) {
                    HStack(alignment: .lastTextBaseline, spacing: 2) {
                        Text(month.pressureText)
                            .fontOpenSans(.body2)
                            .foregroundColor(pressureColor)
                        Text(EntryUnit.mmhg.displayString)
                            .fontOpenSans(.body3)
                            .foregroundColor(theme.textSubheading)
                    }

                    Text(HistoryListStrings.avgPressure)
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.textSubheading)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Average pulse
                VStack(alignment: .leading) {
                    Text("\(month.avgPulse)")
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)

                    Text(HistoryListStrings.avgPulse)
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
