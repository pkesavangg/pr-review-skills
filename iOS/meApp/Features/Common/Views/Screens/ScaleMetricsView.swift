//  ScaleMetricsView.swift
//  meApp
//
//  Created by Barath Chittibabu on 20/06/25.
//
//  A modal sheet that mirrors Ionic's MetricModalPage behaviour.
//  Displays body composition metrics in a horizontally-swipable pager
//  with a scrollable segment header. Each metric presents its current
//  value, measurement date, a short educational blurb, and helpful
//  resources.

//

import SwiftUI

// MARK: - ScaleMetricsView

struct ScaleMetricsView: View {
    // Theme & navigation helpers
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    // Input - can accept either Entry or DTO
    let entryDTO: BathScaleOperationDTO
    let selectedMetric: BodyMetric
    @ObservedObject var dashboardStore: DashboardStore

    // Ordering used throughout the app (mirrors BODY_METRICS_ARRAY)
    private static let metricSequence: [BodyMetric] = [
        .weight, .bmi, .bodyFat, .muscleMass, .water, .pulse, .boneMass,
        .visceralFatLevel, .subcutaneousFatPercent, .proteinPercent,
        .skeletalMusclePercent, .bmr, .metabolicAge
    ]

    private var metricOrder: [BodyMetric] {
        switch dashboardStore.state.metrics.dashboardType {
        case .dashboard4:
            return [.weight, .bmi, .bodyFat, .muscleMass, .water]
        case .dashboard12:
            return Self.metricSequence
        }
    }

    // State
    @State private var selectedMetricState: BodyMetric = .bmi

    // Initialiser to set initial selection
    init(entryDTO: BathScaleOperationDTO, selectedMetric: BodyMetric = .bmi, dashboardStore: DashboardStore) {
        self.entryDTO = entryDTO
        self.selectedMetric = selectedMetric
        self.dashboardStore = dashboardStore
        _selectedMetricState = State(initialValue: selectedMetric)
    }
    
    var body: some View {
        VStack(spacing: 0) {

            // Header bar with close button
            NavbarHeaderView<Image, EmptyView>(
                title: MetricStrings.bodyMetrics,
                leadingContent: { Image(AppAssets.xmark) },
                onLeadingTap: { dismiss() },
                canShowBorder: true,
                canShowPresentationIndicator: true
            )
            .background(theme.backgroundSecondary)

            // Scrollable segment header using reusable component
            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    SegmentedButtonView(segments: metricOrder, selectedSegment: $selectedMetricState)
                        .padding(.horizontal, .spacingSM)
                         .padding(.top, .spacingMD)
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
                    MetricDetailView(
                        entryDTO: entryDTO,
                        metric: metric,
                        measurementLabel: dashboardStore.displayManager.metricInfoDateLabel(for: entryDTO)
                    )
                    .tag(metric)
                }
            }
            .tabViewStyle(PageTabViewStyle(indexDisplayMode: .never))
            .onAppear {
                selectedMetricState = dashboardStore.displayManager.validateMetricInfoSelection(selectedMetricState)
            }
            .onChange(of: dashboardStore.state.metrics.dashboardType) { _, _ in
                selectedMetricState = dashboardStore.displayManager.validateMetricInfoSelection(selectedMetricState)
            }
        }
        .background(theme.backgroundSecondary)
    }
}
