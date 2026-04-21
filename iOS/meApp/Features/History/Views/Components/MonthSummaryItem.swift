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
    @Environment(\.weightlessSettings) private var weightlessSettings
    @Environment(\.weightUnit) private var weightUnit

    let month: HistoryMonth
    /// Localized date formatter: "MMM yyyy"
    private var monthYearText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM"
        guard let date = formatter.date(from: month.id) else { return month.id }
        formatter.dateFormat = "MMM yyyy"
        return formatter.string(from: date)
    }

    private var avgWeightText: String {
        guard let weightValue = month.weight else { return "--" }
        let weight = WeightValueConvertor.formatWeight(weightValue, showSymbol: false, weightUnit: weightUnit, weightless: weightlessSettings)
        let displayValue = ConversionTools.convertStoredToDisplay(weightValue, isMetric: weightUnit == .kg)
        let unitLabel = WeightValueConvertor.unitForDisplay(value: displayValue, unit: weightUnit)
        return String(format: "%@ %@", weight, unitLabel)
    }

    private var changeText: String {
        guard let changeStr = month.change, let changeValue = Double(changeStr) else { return "--" }
        let change = WeightValueConvertor.formatWeight(changeValue, showSymbol: true, weightUnit: weightUnit)
        let displayValue = ConversionTools.convertStoredToDisplay(changeValue, isMetric: weightUnit == .kg)
        let unitLabel = WeightValueConvertor.unitForDisplay(value: displayValue, unit: weightUnit)
        return String(format: "%@ %@", change, unitLabel)
    }

    var body: some View {
      VStack(spacing: 0) {
        HStack {
            // Month & entry count
            VStack(alignment: .leading) {
                Text(monthYearText)
                    .fontOpenSans(.heading5)
                    .foregroundColor(theme.textHeading)

                Text("\(month.count ?? 0) \(HistoryListStrings.entries)")
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)

            }
            .frame(maxWidth: .infinity, alignment: .leading)

            // Average weight
            VStack(alignment: .leading) {
                Text(avgWeightText)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)

                Text(HistoryListStrings.average)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.leading, .spacingMD)

            // Change
            VStack(alignment: .leading) {
                Text(changeText)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)

                Text(HistoryListStrings.change)
                    .fontOpenSans(.body3)
                    .foregroundColor(theme.textSubheading)
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
      MonthSummaryItem(month: month)
            .themeable()
            .environmentObject(Theme.shared)
            .previewLayout(.sizeThatFits)
    }
}
#endif
