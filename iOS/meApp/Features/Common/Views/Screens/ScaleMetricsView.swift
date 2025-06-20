//  ScaleMetricsView.swift
//  meApp
//
//  Created by AI on 20/06/25.
//
//  A modal sheet that mirrors Ionic's MetricModalPage behaviour.
//  Displays body composition metrics in a horizontally-swipable pager
//  with a scrollable segment header. Each metric presents its current
//  value, measurement date, a short educational blurb, and helpful
//  resources.
//
//  Dependencies:
//  • Theme – via `@Environment(\.appTheme)`
//  • Common components – `NavbarHeaderView`, `AppIconView`, `fontOpenSans` util, spacing tokens.
//  • Models – `Entry`, `BodyMetric`, `MetricData`, `BodyMetrics`, `BodyMetricsConvertor`.
//
//  Usage:
//  ```swift
//  ScaleMetricsView(entry: entry, selectedMetric: .bmi)
//      .presentationDetents([.medium, .large])
//  ```
//
//  NOTE: Content strings were converted from the original TypeScript
//  `BODY_METRICS_CONTENT` definition (see src/app/models/Metrics.ts).
//

import SwiftUI

// MARK: - ScaleMetricsView

struct ScaleMetricsView: View {
    // Theme & navigation helpers
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    // Input
    let entry: Entry
    let selectedMetric: BodyMetric

    // Ordering used throughout the app (mirrors BODY_METRICS_ARRAY)
    private static let metricSequence: [BodyMetric] = [
        .weight, .bmi, .bodyFat, .muscleMass, .water, .pulse, .boneMass,
        .visceralFatLevel, .subcutaneousFatPercent, .proteinPercent,
        .skeletalMusclePercent, .bmr, .metabolicAge
    ]

    private var metricOrder: [BodyMetric] { Self.metricSequence }

    // State
    @State private var selectedMetricState: BodyMetric = .bmi

    // Initialiser to set initial selection
    init(entry: Entry, selectedMetric: BodyMetric = .bmi) {
        self.entry = entry
        self.selectedMetric = selectedMetric
        _selectedMetricState = State(initialValue: selectedMetric)
    }

    var body: some View {
        VStack(spacing: 0) {

            // Header bar with close button
            NavbarHeaderView<Image, EmptyView>(
                title: MetricStrings.bodyMetrics,
                leadingContent: { Image(AppAssets.xmark) },
                onLeadingTap: { dismiss() },
                canShowBorder: false
            )
            .background(theme.backgroundSecondary)

            // Scrollable segment header using reusable component
            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    SegmentedButtonView(segments: metricOrder, selectedSegment: $selectedMetricState)
                        .padding(.horizontal, .spacingSM)
                }
                .onAppear {
                    proxy.scrollTo(selectedMetricState, anchor: .center)
                }
                .onChange(of: selectedMetricState) {
                    withAnimation {
                        proxy.scrollTo(selectedMetricState, anchor: .center)
                    }
                }
            }
            .background(theme.backgroundSecondary)

            // Pager with metric-specific details
            TabView(selection: $selectedMetricState) {
                ForEach(metricOrder, id: \.self) { metric in
                    MetricDetailView(entry: entry, metric: metric)
                        .tag(metric)
                }
            }
            .tabViewStyle(PageTabViewStyle(indexDisplayMode: .never))
        }
        .background(theme.backgroundSecondary)
    }
}

