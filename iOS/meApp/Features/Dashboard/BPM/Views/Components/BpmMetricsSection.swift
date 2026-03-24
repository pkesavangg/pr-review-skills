//
//  BpmMetricsSection.swift
//  meApp
//
//  BP-specific metric cards section below the graph.
//  Shows: ThreeReadingAverageCard, AhaRatingCard, streak cards.
//

import SwiftUI

struct BpmMetricsSection: View {
    @ObservedObject var store: DashboardStore
    @Environment(\.appTheme) private var theme

    private var classification: AhaPressureClass {
        store.displayManager?.currentBpmClassification ?? .normal
    }

    var body: some View {
        VStack(spacing: .spacingSM) {
            // Three-Reading Average Card
            if let calc = store.metricsCalculator as? DashboardMetricsCalculator {
                let entries = store.dataManager.state.dailySummaries.compactMap { $0 }
                let visibleOps = store.visibleOperations
                if let avg = calc.getCurrentAverageBP(from: visibleOps) {
                    let threeReadingAvg = ThreeReadingAverage(
                        systolic: avg.systolic,
                        diastolic: avg.diastolic,
                        pulse: avg.pulse,
                        count: min(visibleOps.count, BpmConstants.readingAverageCount),
                        label: ThreeReadingAverage.displayLabel(for: min(visibleOps.count, BpmConstants.readingAverageCount)),
                        classification: avg.classification
                    )
                    ThreeReadingAverageCard(average: threeReadingAvg)
                }
            }

            // AHA Rating Card
            AhaRatingCard(classification: classification)

            // Streaks
            let streakItems = store.streakItemsToShow
            if !streakItems.isEmpty {
                HStack(spacing: .spacingSM) {
                    ForEach(streakItems) { item in
                        streakCard(item)
                    }
                }
            }
        }
        .padding(.horizontal, .spacingSM)
        .padding(.vertical, .spacingSM)
    }

    @ViewBuilder
    private func streakCard(_ item: MetricItem) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(item.label)
                .fontOpenSans(.body3)
                .foregroundColor(theme.textSubheading)
                .textCase(.uppercase)
            Text(item.value)
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.spacingSM)
        .background(theme.backgroundPrimaryDisabled)
        .cornerRadius(12)
    }
}
