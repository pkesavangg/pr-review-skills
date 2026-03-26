//
//  BpmReadingCard.swift
//  meApp
//
//  Individual BP reading row with systolic/diastolic + pulse + AHA color.
//

import SwiftUI

struct BpmReadingCard: View {
    let systolic: Int
    let diastolic: Int
    let pulse: Int
    let date: String
    @Environment(\.appTheme) private var theme

    private var classification: AhaPressureClass {
        AhaPressureClass.classify(systolic: systolic, diastolic: diastolic)
    }

    var body: some View {
        HStack(spacing: .spacingSM) {
            RoundedRectangle(cornerRadius: 4)
                .fill(classification.color(theme: theme))
                .frame(width: 4, height: 32)

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: .spacingXS) {
                    Text("\(systolic)/\(diastolic)")
                        .fontOpenSans(.heading5)
                        .foregroundColor(classification.color(theme: theme))
                    Image(systemName: "heart.fill")
                        .font(.caption2)
                        .foregroundColor(theme.textSubheading)
                    Text("\(pulse)")
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textSubheading)
                }
                Text(date)
                    .fontOpenSans(.body3)
                    .foregroundColor(theme.textSubheading)
            }

            Spacer()

            Text(classification.label)
                .fontOpenSans(.body3)
                .foregroundColor(classification.color(theme: theme))
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(BpmDashboardStrings.bpReadingCardAccessibility(systolic: systolic, diastolic: diastolic, pulse: pulse, label: classification.label, date: date))
    }
}
