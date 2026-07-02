import Foundation

struct WeightValueConvertor {
    /// Returns the correct unit abbreviation for a displayed weight value.
    /// - Parameters:
    ///   - value: Display value in the provided unit (already converted for display, not stored tenths).
    ///   - unit: Target unit for display.
    /// - Returns: "kg" for metric, "lb" for imperial (the "lb" symbol is not pluralized).
    static func unitForDisplay(value: Double, unit: WeightUnit) -> String {
        return unit == .kg ? "kg" : "lb"
    }
    /// Converts and formats a weight value, optionally applying weightless mode and showing plus symbol
    /// - Parameters:
    ///   - value: The weight value to convert
    ///   - showSymbol: Whether to show + symbol for positive values
    ///   - weightUnit: The unit of the weight value
    ///   - weightless: Optional weightless settings to apply
    ///   - isMetric: Whether to use metric units (from AppStatus)
    /// - Returns: Formatted weight string with optional + symbol
    static func formatWeight(
        _ value: Double,
        showSymbol: Bool = false,
        weightUnit: WeightUnit = .kg,
        weightless: WeightlessSettings? = nil
    ) -> String {

        var weight: Double

        // Apply weightless adjustment if needed
        if let weightless = weightless, weightless.isWeightlessOn {
            let weightlessValue = weightless.weightlessWeight ?? 0
            weight = ConversionTools.convertStoredToDisplay(value - weightlessValue, isMetric: weightUnit == .kg)
            // Force show symbol for weightless mode
            return formatWithSymbol(weight, showSymbol: true)
        } else {
            weight = ConversionTools.convertStoredToDisplay(value, isMetric: weightUnit == .kg)
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
