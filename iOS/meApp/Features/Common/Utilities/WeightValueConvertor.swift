import Foundation

struct WeightValueConvertor {
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
        let storedValue = Int(value)

        // Apply weightless adjustment if needed
        if let weightless = weightless, weightless.isWeightlessOn {
          let weightlessValue = Int(weightless.weightlessWeight ?? 0)
            weight = ConversionTools.convertStoredToDisplay(storedValue - weightlessValue, isMetric: weightUnit == .kg)
            // Force show symbol for weightless mode
            return formatWithSymbol(weight, showSymbol: true)
        } else {
            weight = ConversionTools.convertStoredToDisplay(storedValue, isMetric: weightUnit == .kg)
            return formatWithSymbol(weight, showSymbol: showSymbol)
        }
    }

    /// Converts weight between units
    static func convert(value: Double, from: WeightUnit, to: WeightUnit) -> Double {
        // Convert to stored value first (tenths of lbs)
        let stored: Int
        switch from {
        case .kg:
            stored = ConversionTools.convertKgToStored(value)
        case .lb:
            stored = ConversionTools.convertLbsToStored(value)
        }

        // Convert stored value to target unit
        switch to {
        case .kg:
            return ConversionTools.convertStoredToKg(stored)
        case .lb:
            return ConversionTools.convertStoredToLbs(stored)
        }
    }

    /// Helper to format weight with optional plus symbol
    private static func formatWithSymbol(_ value: Double, showSymbol: Bool) -> String {
        if showSymbol && value > 0 {
            return "+\(String(format: "%.1f", value))"
        } else {
            return String(format: "%.1f", value)
        }
    }
}
