import Foundation

struct BabyGraphSelectionPresentation {
    let crosshairDate: Date
    let crosshairValue: Double
    let percentile: Int?
}

/// Resolves baby-chart selection state used by graph rendering overlays.
///
/// Keeps interpolation and percentile logic out of SwiftUI views so chart
/// rendering can stay focused on marks and annotations.
struct GraphSelectionPresentationResolver {

    func babySelectionPresentation(
        babyProfile: BabyProfile?,
        metric: BabyMetric,
        selectedCrosshairDate: Date?,
        plottedPoints: [PlottedGraphSeries],
        plotXDate: (Date) -> Date,
        currentUnit: WeightUnit,
        displayWeight: Double?
    ) -> BabyGraphSelectionPresentation? {
        guard let babyProfile,
              let crosshairDate = selectedCrosshairDate else { return nil }

        let primaryPoints = primarySeriesPoints(in: plottedPoints, metric: metric)
        let plottedDate = plotXDate(crosshairDate)

        guard let crosshairValue = primaryValue(
            at: plottedDate,
            sourceDate: crosshairDate,
            babyProfile: babyProfile,
            metric: metric,
            primaryPoints: primaryPoints,
            displayWeight: displayWeight
        ) else {
            return nil
        }

        return BabyGraphSelectionPresentation(
            crosshairDate: crosshairDate,
            crosshairValue: crosshairValue,
            percentile: percentile(
                for: babyProfile,
                metric: metric,
                value: crosshairValue,
                on: crosshairDate,
                currentUnit: currentUnit
            )
        )
    }

    private func primarySeriesPoints(
        in plottedPoints: [PlottedGraphSeries],
        metric: BabyMetric
    ) -> [PlottedGraphSeries] {
        plottedPoints
            .filter { point in
                switch metric {
                case .height:
                    return BabyDashboardChartSupport.isHeightSeries(point.original.series)
                case .weight:
                    return point.original.series == DashboardStrings.weight
                }
            }
            .sorted { $0.xDate < $1.xDate }
    }

    private func primaryValue(
        at plottedDate: Date,
        sourceDate: Date,
        babyProfile: BabyProfile,
        metric: BabyMetric,
        primaryPoints: [PlottedGraphSeries],
        displayWeight: Double?
    ) -> Double? {
        if let exactValue = primaryPoints
            .first(where: { $0.xDate == plottedDate })?
            .original.value {
            return exactValue
        }

        if let interpolatedValue = interpolatedPrimaryValue(at: plottedDate, primaryPoints: primaryPoints) {
            return interpolatedValue
        }

        return fallbackValue(
            on: sourceDate,
            babyProfile: babyProfile,
            metric: metric,
            displayWeight: displayWeight
        )
    }

    private func interpolatedPrimaryValue(
        at plottedDate: Date,
        primaryPoints: [PlottedGraphSeries]
    ) -> Double? {
        guard let previousPoint = primaryPoints.last(where: { $0.xDate < plottedDate }),
              let nextPoint = primaryPoints.first(where: { $0.xDate > plottedDate }) else {
            return nil
        }

        let lowerTime = previousPoint.xDate.timeIntervalSinceReferenceDate
        let upperTime = nextPoint.xDate.timeIntervalSinceReferenceDate
        let selectedTime = plottedDate.timeIntervalSinceReferenceDate
        let interval = upperTime - lowerTime

        guard interval > AppConstants.Precision.doubleEqualityEpsilon else {
            return previousPoint.original.value
        }

        let progress = (selectedTime - lowerTime) / interval
        return previousPoint.original.value
            + ((nextPoint.original.value - previousPoint.original.value) * progress)
    }

    private func fallbackValue(
        on date: Date,
        babyProfile: BabyProfile,
        metric: BabyMetric,
        displayWeight: Double?
    ) -> Double? {
        switch metric {
        case .height:
            return BabyDashboardChartSupport.dummyHeightValue(for: babyProfile, on: date)
        case .weight:
            guard let displayWeight,
                  abs(displayWeight) >= AppConstants.Precision.doubleEqualityEpsilon else {
                return nil
            }
            return displayWeight
        }
    }

    private func percentile(
        for babyProfile: BabyProfile,
        metric: BabyMetric,
        value: Double,
        on date: Date,
        currentUnit: WeightUnit
    ) -> Int? {
        switch metric {
        case .height:
            return BabyDashboardChartSupport.heightPercentile(
                for: babyProfile,
                heightInches: value,
                on: date
            )
        case .weight:
            let kilograms = currentUnit == .kg ? value : value / 2.20462
            let weightDecigrams = Int((kilograms * BabyPercentileGrowthReference.decigramsToKgFactor).rounded())
            return BabyPercentileGrowthReference.weightPercentile(
                biologicalSex: babyProfile.biologicalSex,
                birthday: BabyDashboardChartSupport.resolvedBirthday(for: babyProfile),
                date: date,
                weightDecigrams: weightDecigrams
            )
        }
    }
}
