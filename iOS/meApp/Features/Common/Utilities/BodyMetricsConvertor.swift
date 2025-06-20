import Foundation

enum BodyMetricsConvertor {
    /// Converts body composition metrics to display format
    /// - Parameters:
    ///   - value: The raw metric value
    ///   - shouldCompose: Whether to apply composition transformation (divide by 10)
    ///   - wholeNumber: Whether to return whole number or decimal
    /// - Returns: Formatted string representation of the metric
    static func convert(_ value: Double?, shouldCompose: Bool = true, wholeNumber: Bool = false) -> String {
        // Handle nil or invalid values
        guard let value = value else {
            return "--"
        }

        // If composition not needed, return raw value
        if !shouldCompose {
            return String(format: "%.0f", value)
        }

        // Apply composition transformation (divide by 10)
        let composedValue = value / 10.0

        // Format based on whether whole number is needed
        if wholeNumber {
            return String(format: "%.0f", composedValue)
        } else {
            return String(format: "%.1f", composedValue)
        }
    }
}
