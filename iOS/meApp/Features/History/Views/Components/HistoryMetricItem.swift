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
    let metricType: BodyMetric
    let value: Int
    let index: Int
    let size: Int
    let onTap: () -> Void
    
    // iOS 17 fix: Prevent tap spam
    @State private var lastTapTime = Date.distantPast
   
    // MARK: - Computed Properties
    /// Returns the background color based on index and total size, matching Android implementation
    private var backgroundColor: Color {
        if size % 2 != 0 {
            // Odd number of metrics: start with backgroundPrimary at index 0, then alternate
            return index % 2 == 0 ? theme.backgroundPrimary : theme.backgroundSecondary
        } else {
            // Even number of metrics: use existing logic (start with backgroundSecondary at index 0)
            return index % 2 != 0 ? theme.backgroundPrimary : theme.backgroundSecondary
        }
    }

    // MARK: - Body

    var body: some View {
        HStack(alignment: .center, spacing: .spacingSM) {
            Text(metric.expandedLabel ?? metric.label)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)

            // Value & Unit
            HStack(alignment: .firstTextBaseline, spacing: 0) {
                if metric.preLabel != nil {
// swiftlint:disable:next force_unwrapping
                    Text(metric.preLabel!)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                }
// swiftlint:disable:next line_length
              Text(BodyMetricsConvertor.convert(Double(metricType == .visceralFatLevel ? value / 10 : value), shouldCompose: metric.bodyCompositionRelated, wholeNumber: metric.isWholeNumber))
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
        .background(backgroundColor)
        .contentShape(Rectangle()) // iOS 17 fix: Ensure consistent tap area
        .onTapGesture {
            // iOS 17 fix: Debounce taps to prevent rapid fire
            let now = Date()
            guard now.timeIntervalSince(lastTapTime) > 0.3 else { return }
            lastTapTime = now
            onTap()
        }
    }
}

#if DEBUG
struct HistoryMetricItem_Previews: PreviewProvider {
    static var previews: some View {
        VStack(spacing: 0) {
            HistoryMetricItem(
// swiftlint:disable:next force_unwrapping
                metric: BodyMetrics.config[.bmi]!,
                metricType: .bmi,
                value: 73,
                index: 0,
                size: 1
            ) {}
        }
        .themeable()
        .environmentObject(Theme.shared)
        .previewLayout(.sizeThatFits)
    }
}
#endif
