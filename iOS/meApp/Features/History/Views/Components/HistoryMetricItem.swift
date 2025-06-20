//
//  HistoryMetricItem.swift
//  meApp
//
//  Created by Barath Chittibabu on 17/06/25.
//

// MARK: - HistoryMetricItem

/// Visual row used inside `HistoryEntryItem` to display an individual metric (e.g. BMI, Body Fat).

import SwiftUI

struct HistoryMetricItem: View {
    @Environment(\.appTheme) private var theme

    let metric: MetricData
    let value: Int
    let isAlternate: Bool
    let onTap: () -> Void

    // MARK: - Body

    var body: some View {
        HStack(alignment: .center, spacing: .spacingSM) {
            // Title
            Text(metric.label)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)

            // Value & Unit
            HStack(alignment: .firstTextBaseline, spacing: 0) {
                if metric.preLabel != nil {
                    Text(metric.preLabel!)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                }
              Text(BodyMetricsConvertor.convert(Double(value), shouldCompose: metric.bodyCompositionRelated, wholeNumber: metric.isWholeNumber))
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)

                if !metric.unit.isEmpty {
                    Text(metric.unit)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                }
            }

            if !metric.icon.isEmpty {
                AppIconView(icon: metric.icon)
                    .foregroundColor(theme.actionPrimary)
            }
        }
        .padding(.vertical, .spacingSM)
        .padding(.horizontal, .spacingSM)
        .background(isAlternate ? theme.backgroundPrimary : theme.backgroundSecondary)
        .onTapGesture {
            onTap()
        }
    }
}

#if DEBUG
struct HistoryMetricItem_Previews: PreviewProvider {
    static var previews: some View {
        VStack(spacing: 0) {
            HistoryMetricItem(
                metric: BodyMetrics.config[.bmi]!,
                value: 73,
                isAlternate: false,
                onTap: {}
            )
        }
        .themeable()
        .environmentObject(Theme.shared)
        .previewLayout(.sizeThatFits)
    }
}
#endif
