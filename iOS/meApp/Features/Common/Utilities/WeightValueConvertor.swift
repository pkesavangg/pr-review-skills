import Foundation

struct WeightValueConvertor {
    /// Returns the correct unit abbreviation for a displayed weight value.
    /// - Parameters:
    ///   - value: Display value in the provided unit (already converted for display, not stored tenths).
    ///   - unit: Target unit for display.
    /// - Returns: "kg" for metric; for imperial returns "lb" or "lbs" using these rules:
    ///            0.0 → "lbs", 0.35 → "lb", 1 → "lb", 2.3 → "lbs"
    static func unitForDisplay(value: Double, unit: WeightUnit) -> String {
        if unit == .kg { return "kg" }
        let epsilon = AppConstants.Precision.doubleEqualityEpsilon
        let magnitude = abs(value)
        if magnitude < epsilon { return "lbs" }
        let isInteger = abs(magnitude - magnitude.rounded()) < epsilon
        let displayedMagnitude: Double = isInteger
            ? magnitude.rounded()
            : (magnitude * 10).rounded() / 10
        if displayedMagnitude < 1.0 - epsilon { return "lb" }
        if abs(displayedMagnitude - 1.0) <= epsilon { return "lb" }
        return "lbs"
    }
    /// Converts and formats a weight value, optionally applying weightless mode and showing plus symbol
    /// - Parameters:
    ///   - value: The weight value to convert
    ///   - showSymbol: Whether to show + symbol for positive values
    ///   - weightUnit: The unit of the weight value
    ///   - weightless: Optional weightless settings to apply
    ///   - isMetric: Whether to use metric units (from AppStatus)
    /// - Returns: Formatted weight string with optional + symbol
    static func formatWeight(_ value: Double,
                           showSymbol: Bool = false,
                           weightUnit: WeightUnit = .kg,
                           weightless: WeightlessSettings? = nil
    ) -> String {

        var weight: Double

        // Convert the input value to stored format (tenths of lbs)
        let storedValue = Int(round(value))

        // Apply weightless adjustment if needed
        if let weightless = weightless, weightless.isWeightlessOn {
            let weightlessValue = Int(round(weightless.weightlessWeight ?? 0))
            weight = ConversionTools.convertStoredToDisplay(storedValue - weightlessValue, isMetric: weightUnit == .kg)
            // Force show symbol for weightless mode
            return formatWithSymbol(weight, showSymbol: true)
        } else {
            weight = ConversionTools.convertStoredToDisplay(storedValue, isMetric: weightUnit == .kg)
            return formatWithSymbol(weight, showSymbol: showSymbol)
        }
    }

    /// Helper to format weight with optional plus symbol
    private static func formatWithSymbol(_ value: Double, showSymbol: Bool) -> String {
        // Drop trailing .0 for integers; keep one decimal otherwise
        let isInteger = abs(value - value.rounded()) < AppConstants.Precision.doubleEqualityEpsilon
        if showSymbol && value > 0 {
            if isInteger {
                return "+\(Int(value.rounded()))"
            } else {
                return "+" + String(format: "%.1f", value)
            }
        } else {
            if isInteger {
                return "\(Int(value.rounded()))"
            } else {
                return String(format: "%.1f", value)
            }
        }
    }
}
