//
//  WeightSnapshotCard.swift
//  meApp
//
//  Mini weight trend card for the multi-device snapshot dashboard.
//

import Charts
import SwiftUI

struct WeightSnapshotCard: View {
    let summaries: [BathScaleWeightSummary]
    let onTap: () -> Void
    @Environment(\.appTheme) private var theme

    private var lastWeekSummaries: [BathScaleWeightSummary] {
        let sevenDaysAgo = Calendar.current.date(byAdding: .day, value: -7, to: Date()) ?? Date()
        return summaries.filter { $0.date >= sevenDaysAgo }
    }

    private var averageWeight: String {
        let weights = lastWeekSummaries.map(\.weight).filter { $0 > 0 }
        guard !weights.isEmpty else { return "--" }
        let avg = weights.reduce(0, +) / Double(weights.count)
        return String(format: "%.1f", avg / 10.0)
    }

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: .spacingXS) {
                Text(ProductTypeStrings.myWeight)
                    .fontOpenSans(.heading5)
                    .foregroundColor(theme.textHeading)

                if lastWeekSummaries.isEmpty {
                    Text(BpmDashboardStrings.noReadingsYet)
                        .fontOpenSans(.body3)
                        .foregroundColor(theme.textSubheading)
                } else {
                    Text("Week avg: \(averageWeight)")
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)

                    miniChart()
                        .frame(height: 60)
                }
            }
            .padding(.spacingSM)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(theme.backgroundPrimaryDisabled)
            .cornerRadius(12)
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func miniChart() -> some View {
        Chart(lastWeekSummaries, id: \.period) { summary in
            LineMark(
                x: .value("Date", summary.date),
                y: .value("Weight", summary.weight)
            )
            .foregroundStyle(theme.actionPrimary)
            .interpolationMethod(.monotone)
        }
        .chartXAxis(.hidden)
        .chartYAxis(.hidden)
        .chartLegend(.hidden)
    }
}
