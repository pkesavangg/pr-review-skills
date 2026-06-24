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
        // Helper to process the value
        func format(_ inputValue: Double?) -> String? {
            // Try to use the primary value first
            if let value = inputValue {
                // Apply composition transformation if needed
                let processedValue = shouldCompose ? value / 10.0 : value
                
                // Check if the processed value is valid (not NaN, infinite, or unreasonably negative)
                if !processedValue.isNaN && !processedValue.isInfinite && processedValue >= 0 {
                    // Apply robust rounding logic for 1 decimal place
                    let roundedValue = (processedValue * 10).rounded(.toNearestOrAwayFromZero) / 10
                    
                    // If the rounded value is zero, return placeholder instead of "0" or "0.0"
                    if roundedValue == 0 {
                        return "--"
                    }
                    
                    // Format the value based on requirements
                    return wholeNumber
                    ? String(format: "%.0f", roundedValue)
                    : String(format: "%.1f", roundedValue)
                }
            }
            return nil
        }
        
        return format(value) ?? format(fallbackValue) ?? "--"
    }
}
