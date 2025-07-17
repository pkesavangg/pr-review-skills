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
        print("hello: BodyMetricsConvertor.convert called with value: \(value ?? -999), shouldCompose: \(shouldCompose), wholeNumber: \(wholeNumber), fallbackValue: \(fallbackValue ?? -999)")
        
        // Try to use the primary value first
        if let value = value {
            print("hello: Processing primary value: \(value)")
            // Apply composition transformation if needed
            let processedValue = shouldCompose ? value / 10.0 : value
            print("hello: After composition processing: \(processedValue)")
            
            // Check if the processed value is valid (not NaN, infinite, or unreasonably negative)
            if !processedValue.isNaN && !processedValue.isInfinite && processedValue >= 0 {
                print("hello: Primary value is valid, formatting...")
                // Format the value based on requirements
                if wholeNumber {
                    let result = String(format: "%.0f", processedValue)
                    print("hello: Returning whole number result: \(result)")
                    return result
                } else {
                    let result = String(format: "%.1f", processedValue)
                    print("hello: Returning decimal result: \(result)")
                    return result
                }
            } else {
                print("hello: Primary value is invalid - isNaN: \(processedValue.isNaN), isInfinite: \(processedValue.isInfinite), < 0: \(processedValue < 0)")
            }
        } else {
            print("hello: Primary value is nil")
        }
        
        // If primary value is invalid, try fallback value
        if let fallbackValue = fallbackValue {
            print("hello: Trying fallback value: \(fallbackValue)")
            // Apply composition transformation if needed
            let processedFallback = shouldCompose ? fallbackValue / 10.0 : fallbackValue
            print("hello: After composition processing fallback: \(processedFallback)")
            
            // Check if the fallback value is valid
            if !processedFallback.isNaN && !processedFallback.isInfinite && processedFallback >= 0 {
                print("hello: Fallback value is valid, formatting...")
                // Format the fallback value
                if wholeNumber {
                    let result = String(format: "%.0f", processedFallback)
                    print("hello: Returning fallback whole number result: \(result)")
                    return result
                } else {
                    let result = String(format: "%.1f", processedFallback)
                    print("hello: Returning fallback decimal result: \(result)")
                    return result
                }
            } else {
                print("hello: Fallback value is invalid - isNaN: \(processedFallback.isNaN), isInfinite: \(processedFallback.isInfinite), < 0: \(processedFallback < 0)")
            }
        } else {
            print("hello: No fallback value available")
        }
        
        // If both primary and fallback values are invalid, return placeholder
        print("hello: Both primary and fallback values are invalid, returning --")
        return "--"
    }
}
