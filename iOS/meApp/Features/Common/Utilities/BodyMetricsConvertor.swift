import Foundation

enum BodyMetricsConvertor {
    /// Converts body composition metrics to display format
    /// - Parameters:
    ///   - value: The raw metric value
    ///   - shouldCompose: Whether to apply composition transformation (divide by 10)
    ///   - wholeNumber: Whether to return whole number or decimal
    ///   - fallbackValue: Optional fallback value to use if primary value is zero/nil
    /// - Returns: Formatted string representation of the metric
    static func convert(_ value: Double?, shouldCompose: Bool = true, wholeNumber: Bool = false, fallbackValue: Double? = nil) -> String {
        // Try to use the primary value first
        if let value = value {
            // Apply composition transformation if needed
            let processedValue = shouldCompose ? value / 10.0 : value
            

            // Check if the processed value is valid (not NaN, infinite, or unreasonably negative)
            if !processedValue.isNaN && !processedValue.isInfinite && processedValue >= 0 {
                // Apply robust rounding logic for 1 decimal place
                let roundedValue = (processedValue * 10).rounded(.toNearestOrAwayFromZero) / 10
                
                // Format the value based on requirements
                if wholeNumber {
                    return String(format: "%.0f", roundedValue)
                } else {
                    return String(format: "%.1f", roundedValue)
                }
            }
        }

        // If primary value is invalid, try fallback value
        if let fallbackValue = fallbackValue {
            let processedFallback = shouldCompose ? fallbackValue / 10.0 : fallbackValue

            if !processedFallback.isNaN && !processedFallback.isInfinite && processedFallback >= 0 {
                // Apply robust rounding logic for 1 decimal place
                let roundedFallback = (processedFallback * 10).rounded(.toNearestOrAwayFromZero) / 10
                
                if wholeNumber {
                    return String(format: "%.0f", roundedFallback)
                } else {
                    return String(format: "%.1f", roundedFallback)
                }
            }
        }

        // If both primary and fallback values are invalid, return placeholder
        return "--"
    }
}
