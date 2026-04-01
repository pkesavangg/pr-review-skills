//
//  BabyTrendViewModel.swift
//  meApp
//
//  View model for the baby trend screen.
//  Keeps presentation and store mutation logic out of the SwiftUI view.
//

import Foundation

struct BabyTrendDisplayState {
    let selectedMetric: BabyMetric
    let headlineLabel: String
    let weightDisplay: (lbs: String, oz: String)
    let heightDisplayText: String
}

@MainActor
final class BabyTrendViewModel {
    func displayState(
        dashboardStore: DashboardStore,
        babyProfile: BabyProfile
    ) -> BabyTrendDisplayState {
        let selectedMetric = selectedMetric(in: dashboardStore)
        let displayWeight = currentDisplayWeight(
            dashboardStore: dashboardStore,
            babyProfile: babyProfile
        )
        let displayHeight = currentDisplayHeight(
            dashboardStore: dashboardStore,
            babyProfile: babyProfile
        )

        return BabyTrendDisplayState(
            selectedMetric: selectedMetric,
            headlineLabel: headlineLabel(in: dashboardStore),
            weightDisplay: weightLbsOz(for: displayWeight),
            heightDisplayText: displayHeight > 0 ? String(format: "%.1f", displayHeight) : "--.-"
        )
    }

    func handleAppear(dashboardStore: DashboardStore) {
        applySelectedMetric(selectedMetric(in: dashboardStore), to: dashboardStore, clearSelection: false)
    }

    func handlePeriodChange(dashboardStore: DashboardStore) {
        dashboardStore.forceImmediateUIUpdate()
    }

    func applySelectedMetric(
        _ metric: BabyMetric,
        to dashboardStore: DashboardStore,
        clearSelection: Bool = true
    ) {
        dashboardStore.state.ui.selectedMetricLabel = metric == .height ? BabyMetric.height.rawValue : nil
        if clearSelection {
            dashboardStore.chartManager.clearSelection()
        }
        dashboardStore.chartManager.updateYAxisCache(force: true)
        dashboardStore.scheduleUIUpdate()
    }

    private func selectedMetric(in dashboardStore: DashboardStore) -> BabyMetric {
        dashboardStore.state.ui.selectedMetricLabel == BabyMetric.height.rawValue ? .height : .weight
    }

    private func headlineLabel(in dashboardStore: DashboardStore) -> String {
        if hasPointSelected(in: dashboardStore) {
            return dashboardStore.displayManager.weightDisplayLabel
        }
        return "\(dashboardStore.state.graph.selectedPeriod.rawValue) average"
    }

    private func hasPointSelected(in dashboardStore: DashboardStore) -> Bool {
        dashboardStore.state.graph.selectedXValue != nil || dashboardStore.state.graph.selectedPoint != nil
    }

    private func fallbackBabyOperations(
        dashboardStore: DashboardStore,
        babyProfile: BabyProfile
    ) -> [BathScaleWeightSummary] {
        BabyDashboardChartSupport.dummySummaries(
            for: babyProfile,
            period: dashboardStore.state.graph.selectedPeriod
        )
    }

    private func operationsForCurrentAverage(
        dashboardStore: DashboardStore,
        babyProfile: BabyProfile
    ) -> [BathScaleWeightSummary] {
        let labelRangeOperations = dashboardStore.displayManager.getOperationsForLabelDateRange()
        if !labelRangeOperations.isEmpty {
            return labelRangeOperations
        }

        let visibleOperations = dashboardStore.visibleOperations
        if !visibleOperations.isEmpty {
            return visibleOperations
        }

        let continuousOperations = dashboardStore.continuousOperations
        if !continuousOperations.isEmpty {
            return continuousOperations
        }

        return fallbackBabyOperations(dashboardStore: dashboardStore, babyProfile: babyProfile)
    }

    private func currentDisplayWeight(
        dashboardStore: DashboardStore,
        babyProfile: BabyProfile
    ) -> Double {
        if hasPointSelected(in: dashboardStore),
           let displayWeight = dashboardStore.displayManager.displayWeight,
           abs(displayWeight) >= AppConstants.Precision.doubleEqualityEpsilon {
            return displayWeight
        }

        let currentAverageWeight = averageWeight(
            for: operationsForCurrentAverage(dashboardStore: dashboardStore, babyProfile: babyProfile),
            dashboardStore: dashboardStore
        )
        if abs(currentAverageWeight) >= AppConstants.Precision.doubleEqualityEpsilon {
            return currentAverageWeight
        }

        let fallbackBabyAverage = averageWeight(
            for: fallbackBabyOperations(dashboardStore: dashboardStore, babyProfile: babyProfile),
            dashboardStore: dashboardStore
        )
        if abs(fallbackBabyAverage) >= AppConstants.Precision.doubleEqualityEpsilon {
            return fallbackBabyAverage
        }

        if let displayWeight = dashboardStore.displayManager.displayWeight,
           abs(displayWeight) >= AppConstants.Precision.doubleEqualityEpsilon {
            return displayWeight
        }

        let fallbackAverage = dashboardStore.displayManager.getCurrentAverageWeight()
        return abs(fallbackAverage) >= AppConstants.Precision.doubleEqualityEpsilon ? fallbackAverage : 0
    }

    private func currentDisplayHeight(
        dashboardStore: DashboardStore,
        babyProfile: BabyProfile
    ) -> Double {
        if let selectedDate = dashboardStore.state.graph.selectedXValue ?? dashboardStore.state.graph.selectedPoint?.date {
            return BabyDashboardChartSupport.dummyHeightValue(for: babyProfile, on: selectedDate)
        }

        let operations = operationsForCurrentAverage(dashboardStore: dashboardStore, babyProfile: babyProfile)
        let visibleDates = operations.isEmpty
            ? fallbackBabyOperations(dashboardStore: dashboardStore, babyProfile: babyProfile).map(\.date)
            : operations.map(\.date)
        return BabyDashboardChartSupport.averageDummyHeight(for: babyProfile, dates: visibleDates)
    }

    private func weightLbsOz(for weight: Double) -> (lbs: String, oz: String) {
        guard weight > 0 else { return (lbs: "--", oz: "--") }
        let wholeLbs = Int(weight)
        let remainingOz = (weight - Double(wholeLbs)) * 16.0
        return (lbs: "\(wholeLbs)", oz: String(format: "%.1f", remainingOz))
    }

    private func averageWeight(
        for operations: [BathScaleWeightSummary],
        dashboardStore: DashboardStore
    ) -> Double {
        guard !operations.isEmpty else { return 0 }

        let weights = operations.map { dashboardStore.goalManager.convertWeightToDisplay(Int($0.weight.rounded())) }
        guard !weights.isEmpty else { return 0 }

        let average = weights.reduce(0, +) / Double(weights.count)
        return (average * 10).rounded(.toNearestOrAwayFromZero) / 10
    }
}
