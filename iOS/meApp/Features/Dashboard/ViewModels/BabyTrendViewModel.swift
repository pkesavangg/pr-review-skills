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
    let weightDisplay: BabyWeightDisplay
    let heightDisplayText: String
}

struct BabyGrowthPercentilesSheetState {
    let weightDisplay: BabyWeightDisplay
    let weightPercentileText: String
    let heightDisplayText: String
    let heightPercentileText: String
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
            weightDisplay: weightDisplay(for: displayWeight, units: dashboardStore.currentMeasurementUnits),
            heightDisplayText: heightDisplayText(for: displayHeight)
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
        guard dashboardStore.hasBabyEntries else { return DashboardStrings.noEntries }
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
        guard dashboardStore.hasBabyEntries else { return 0 }
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

    func growthPercentilesSheetState(
        dashboardStore: DashboardStore,
        babyProfile: BabyProfile
    ) -> BabyGrowthPercentilesSheetState {
        let displayWeight = currentDisplayWeight(
            dashboardStore: dashboardStore,
            babyProfile: babyProfile
        )
        let displayHeight = currentDisplayHeight(
            dashboardStore: dashboardStore,
            babyProfile: babyProfile
        )

        return BabyGrowthPercentilesSheetState(
            weightDisplay: weightDisplay(for: displayWeight, units: dashboardStore.currentMeasurementUnits),
            weightPercentileText: percentileText(
                weightPercentile(
                    dashboardStore: dashboardStore,
                    babyProfile: babyProfile,
                    displayWeight: displayWeight
                )
            ),
            heightDisplayText: heightDisplayText(for: displayHeight),
            heightPercentileText: percentileText(
                heightPercentile(
                    dashboardStore: dashboardStore,
                    babyProfile: babyProfile,
                    displayHeight: displayHeight
                )
            )
        )
    }

    private func weightDisplay(for weight: Double, units: MeasurementUnits) -> BabyWeightDisplay {
        guard weight > 0 else {
            return BabyDashboardChartSupport.emptyWeightDisplay(for: units)
        }
        switch units {
        case .metric:
            return BabyWeightDisplay(
                primary: String(format: "%.3f", weight),
                primaryUnit: BabyDashboardStrings.kg,
                secondary: nil,
                secondaryUnit: nil
            )
        case .imperialLbDecimal:
            return BabyWeightDisplay(
                primary: String(format: "%.1f", weight),
                primaryUnit: BabyDashboardStrings.lb,
                secondary: nil,
                secondaryUnit: nil
            )
        case .imperialLbOz:
            var wholeLbs = Int(weight)
            let rawOz = (weight - Double(wholeLbs)) * 16.0
            let roundedOz = (rawOz * 10).rounded() / 10
            var remainingOz = roundedOz
            if roundedOz >= 16.0 { wholeLbs += 1; remainingOz = 0.0 }
            return BabyWeightDisplay(
                primary: "\(wholeLbs)",
                primaryUnit: BabyDashboardStrings.lbs,
                secondary: String(format: "%.1f", remainingOz),
                secondaryUnit: BabyDashboardStrings.oz
            )
        }
    }

    private func heightDisplayText(for height: Double) -> String {
        height > 0 ? String(format: "%.1f", height) : "--.-"
    }

    private func percentileText(_ percentile: Int?) -> String {
        guard let percentile else { return "--" }
        return "\(percentile)"
    }

    private func selectedDate(in dashboardStore: DashboardStore) -> Date? {
        dashboardStore.state.graph.selectedXValue ?? dashboardStore.state.graph.selectedPoint?.date
    }

    private func weightPercentile(
        dashboardStore: DashboardStore,
        babyProfile: BabyProfile,
        displayWeight: Double
    ) -> Int? {
        if let selectedDate = selectedDate(in: dashboardStore) {
            return weightPercentile(
                for: displayWeight,
                on: selectedDate,
                dashboardStore: dashboardStore,
                babyProfile: babyProfile
            )
        }

        let operations = operationsForCurrentAverage(dashboardStore: dashboardStore, babyProfile: babyProfile)
        let sourceOperations = operations.isEmpty
            ? fallbackBabyOperations(dashboardStore: dashboardStore, babyProfile: babyProfile)
            : operations

        let percentiles = sourceOperations.compactMap { summary in
            let weight = dashboardStore.goalManager.convertWeightToDisplay(Int(summary.weight.rounded()))
            return weightPercentile(
                for: weight,
                on: summary.date,
                dashboardStore: dashboardStore,
                babyProfile: babyProfile
            )
        }

        guard !percentiles.isEmpty else { return nil }
        return Int((Double(percentiles.reduce(0, +)) / Double(percentiles.count)).rounded())
    }

    private func weightPercentile(
        for displayWeight: Double,
        on date: Date,
        dashboardStore: DashboardStore,
        babyProfile: BabyProfile
    ) -> Int? {
        guard displayWeight > 0 else { return nil }
        let weightDecigrams = weightDecigrams(from: displayWeight, unit: dashboardStore.currentUnit)
        return BabyPercentileGrowthReference.weightPercentile(
            biologicalSex: babyProfile.biologicalSex,
            birthday: BabyDashboardChartSupport.resolvedBirthday(for: babyProfile),
            date: date,
            weightDecigrams: weightDecigrams
        )
    }

    private func heightPercentile(
        dashboardStore: DashboardStore,
        babyProfile: BabyProfile,
        displayHeight: Double
    ) -> Int? {
        if let selectedDate = selectedDate(in: dashboardStore) {
            guard displayHeight > 0 else { return nil }
            return BabyDashboardChartSupport.heightPercentile(
                for: babyProfile,
                heightInches: displayHeight,
                on: selectedDate
            )
        }

        let operations = operationsForCurrentAverage(dashboardStore: dashboardStore, babyProfile: babyProfile)
        let sourceDates = operations.isEmpty
            ? fallbackBabyOperations(dashboardStore: dashboardStore, babyProfile: babyProfile).map(\.date)
            : operations.map(\.date)

        let percentiles = sourceDates.map { date in
            BabyDashboardChartSupport.heightPercentile(
                for: babyProfile,
                heightInches: BabyDashboardChartSupport.dummyHeightValue(for: babyProfile, on: date),
                on: date
            )
        }

        guard !percentiles.isEmpty else { return nil }
        return Int((Double(percentiles.reduce(0, +)) / Double(percentiles.count)).rounded())
    }

    private func weightDecigrams(from displayWeight: Double, unit: WeightUnit) -> Int {
        let kilograms = unit == .kg ? displayWeight : (displayWeight / 2.20462)
        return Int((kilograms * BabyPercentileGrowthReference.decigramsToKgFactor).rounded())
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
