import Foundation

/// Rounding helpers for weightless-mode arithmetic ("current - baseline").
///
/// These ensure that subtraction operates on values the user actually sees
/// on screen, avoiding +/-0.1 discrepancies caused by subtracting raw
/// floating-point intermediates.
enum WeightlessDisplayRounding {

    /// Rounds a weight value to 1 decimal place for display.
    static func roundedToDisplayedWeight(_ value: Double) -> Double {
        (value * 10).rounded(.toNearestOrAwayFromZero) / 10
    }

    /// Rounds each weight to its displayed tenths value, then averages.
    static func displayedAverageWeight(_ weights: [Double]) -> Double {
        guard !weights.isEmpty else { return 0 }
        let displayedTenths = weights.map { Int(($0 * 10).rounded(.toNearestOrAwayFromZero)) }
        let averageTenths = Double(displayedTenths.reduce(0, +)) / Double(displayedTenths.count)
        return averageTenths.rounded(.toNearestOrAwayFromZero) / 10
    }

    /// Rounds both operands to displayed precision, then subtracts.
    static func displayedWeightlessDifference(
        currentWeight: Double,
        anchorWeight: Double
    ) -> Double {
        let displayedCurrent = roundedToDisplayedWeight(currentWeight)
        let displayedAnchor = roundedToDisplayedWeight(anchorWeight)
        return roundedToDisplayedWeight(displayedCurrent - displayedAnchor)
    }

    /// Averages displayed weights, then subtracts the displayed anchor.
    static func displayedWeightlessAverageDifference(
        currentWeights: [Double],
        anchorWeight: Double
    ) -> Double {
        guard !currentWeights.isEmpty else { return 0 }
        let displayedCurrentAverage = displayedAverageWeight(currentWeights)
        let displayedAnchor = roundedToDisplayedWeight(anchorWeight)
        return roundedToDisplayedWeight(displayedCurrentAverage - displayedAnchor)
    }
}
