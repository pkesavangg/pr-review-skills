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

    /// Interpolation over the plotted points is delegated to the shared graph spline (the same Fritsch–Carlson
    /// Hermite the weight/BPM graphs use), so a gap crosshair value matches the header. (MOB-1516)
    private let dataPreparer = GraphDataPreparer()

    // swiftlint:disable:next function_parameter_count
    func babySelectionPresentation(
        babyProfile: BabyProfile?,
        metric: BabyMetric,
        selectedCrosshairDate: Date?,
        percentileDate: Date?,
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
            // MOB-1591: percentiles are AGE-driven, so they must use the reading's REAL date, not the plotted
            // x-date. In year/total the plotted point is the monthly aggregate collapsed to the 1st (e.g. Jun 1),
            // which is a younger age and yields a different (wrong) percentile than week/month for the same
            // reading. `percentileDate` carries the real entry date (the summary's `entryTimestamp`); fall back
            // to the crosshair date for gap/in-between selections (no real reading → nothing more precise).
            percentile: percentile(
                for: babyProfile,
                metric: metric,
                value: crosshairValue,
                on: percentileDate ?? crosshairDate,
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

    // swiftlint:disable:next function_parameter_count
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

        // MOB-1516: gap selection → Hermite-interpolate over the plotted points (parity with the weight/BPM
        // graphs), replacing the previous linear 2-point lerp so the crosshair value matches the header.
        if let interpolated = dataPreparer.interpolatedPlottedValue(at: plottedDate, points: primaryPoints) {
            return interpolated
        }

        return fallbackValue(
            on: sourceDate,
            babyProfile: babyProfile,
            metric: metric,
            displayWeight: displayWeight
        )
    }

    private func fallbackValue(
        on _: Date,
        babyProfile _: BabyProfile,
        metric: BabyMetric,
        displayWeight: Double?
    ) -> Double? {
        switch metric {
        case .height:
            // No synthetic fill: a crosshair outside the recorded length points has no value.
            return nil
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
        // MOB-1516 audit: percentiles are age-driven, so without a known birthday there is no valid answer
        // (parity with Smart Baby, which shows none). The crosshair VALUE still renders; only the % is hidden.
        guard BabyDashboardChartSupport.canResolveGrowthPercentiles(for: babyProfile) else { return nil }
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
