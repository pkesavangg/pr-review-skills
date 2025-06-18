//
//  MonthSummaryItem.swift
//  meApp
//
//  Created by Barath Chittibabu on 17/06/25.
//

import SwiftUI

/// Reusable list-row that shows a single month summary in the History list screen.
/// Mirrors the visual spec: Month + year, entry count, average weight, weight change, chevron.
struct MonthSummaryItem: View {
    @Environment(\.appTheme) private var theme

    let month: HistoryMonth
    let weightUnit: String
    /// Localized date formatter: "MMM yyyy"
    private var monthYearText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM"
        guard let date = formatter.date(from: month.id) else { return month.id }
        formatter.dateFormat = "MMM yyyy"
        return formatter.string(from: date)
    }

    private var avgWeightText: String {
        guard let w = month.weight else { return "--" }
        return String(format: "%.1f %@", w, weightUnit)
    }

    private var changeText: String {
        guard let cStr = month.change, let c = Double(cStr) else { return "--" }
        return String(format: "%@%.1f %@", c >= 0 ? "+" : "-", c, weightUnit)
    }

    var body: some View {
        HStack(spacing: .spacingMD) {
            // Month & entry count
            VStack(alignment: .leading) {
                Text(monthYearText)
                    .fontOpenSans(.heading5)
                    .foregroundColor(theme.textHeading)
                Text("\(month.count ?? 0) \(HistoryListStrings.entries)")
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
            }
            Spacer(minLength: .spacingMD)
            // Average weight
            VStack(alignment: .leading) {
                Text(avgWeightText)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)
                Text(HistoryListStrings.average)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
            }
            Spacer(minLength: .spacingMD)

            // Change
            VStack(alignment: .leading) {
                Text(changeText)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)
                Text(HistoryListStrings.change)
                    .fontOpenSans(.body3)
                    .foregroundColor(theme.textSubheading)
            }
            // Chevron icon
            AppIconView(icon: AppAssets.chevronRight)
                .foregroundColor(theme.statusIconPrimary)
        }
        .padding(.vertical, .spacingSM)
    }
}

#if DEBUG
struct MonthSummaryItem_Previews: PreviewProvider {
    static var previews: some View {
        let month = HistoryMonth(
            id: "2025-12",
            weight: 148.6,
            entryTimestamp: "2025-12",
            count: 5,
            weights: "",
            change: "",
            bodyFat: nil,
            muscleMass: nil,
            water: nil,
            bmi: nil,
            date: nil,
            time: nil,
            month: "12",
            year: "2025",
            min: nil,
            max: nil
        )
        MonthSummaryItem(month: month, weightUnit: "lbs")
            .themeable()
            .environmentObject(Theme.shared)
            .previewLayout(.sizeThatFits)
    }
}
#endif


