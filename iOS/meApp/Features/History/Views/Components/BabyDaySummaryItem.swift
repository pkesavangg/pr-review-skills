//
//  BabyDaySummaryItem.swift
//  meApp
//

import SwiftUI

/// Reusable list-row that shows a single day summary for baby history.
struct BabyDaySummaryItem: View {
    @Environment(\.appTheme) private var theme

    let day: BabyHistoryDay

    private var combinedAccessibilityLabel: String {
        let birthdayPrefix = day.isBirthday ? "\(HistoryListStrings.accBirthdayBalloonLabel), " : ""
        return birthdayPrefix
            + "\(dateText), \(day.entryCount) \(HistoryListStrings.entries), "
            + "\(HistoryListStrings.weight) \(weightText), "
            + "\(HistoryListStrings.length) \(lengthText)"
    }

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

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                // Date & entry count — balloon precedes the date on the baby's
                // birthday (MOB-1164). The balloon aligns with the date line, with the
                // entry count sitting below at the row's leading edge (matches the mock).
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: .spacingXS) {
                        if day.isBirthday {
                            BirthdayBalloonBadge()
                        }
                        Text(dateText)
                            .fontOpenSans(.heading5)
                            .foregroundColor(theme.textHeading)
                    }

                    Text("\(day.entryCount) \(HistoryListStrings.entries)")
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Weight
                VStack(alignment: .leading) {
                    BabyValueText(value: weightText)
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
                    BabyValueText(value: lengthText)

                    Text(HistoryListStrings.length)
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
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(combinedAccessibilityLabel)
            .accessibilityHint(HistoryListStrings.accDayRowHint)
            .accessibilityAddTraits(.isButton)
            Divider()
                .foregroundColor(theme.actionPrimary)
        }
    }
}

/// Section header for a baby history week group.
struct BabyWeekHeaderView: View {
    @Environment(\.appTheme) private var theme

    let weekNumber: Int
    /// Shows the birthday balloon before the week label when this week contains
    /// the baby's birthday (MOB-1164).
    var showBirthdayBalloon = false

    var body: some View {
        HStack(spacing: .spacingXS) {
            if showBirthdayBalloon {
                BirthdayBalloonBadge()
            }
            Text("\(HistoryListStrings.week) \(weekNumber)")
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textBody)
            Spacer()
        }
        .padding(.horizontal, .spacingSM)
        .padding(.top, .spacingMD)
        .padding(.bottom, .spacingXS)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}
