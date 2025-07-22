import SwiftUI

// MARK: - YAxisScale Model

struct YAxisScale {
    let min: Double
    let max: Double
    let step: Double
    let ticks: [Double]
    let domain: ClosedRange<Double>
    let average: Double
}

// MARK: - YAxisCalculator

struct YAxisCalculator {

    /// Calculate Y-axis scale for chart display
    /// - Parameters:
    ///   - operations: Array of weight summaries
    ///   - goalWeight: Goal weight for display
    ///   - isWeightlessMode: Whether weightless mode is enabled
    ///   - anchorWeight: Anchor weight for weightless mode
    ///   - convertStoredWeightToDisplay: Function to convert stored weight to display weight
    ///   - chartHeight: Chart height for optimal tick calculation
    ///   - lastScale: Previous YAxisScale to use as fallback when no data
    /// - Returns: YAxisScale with calculated domain and ticks
    static func calculateYAxis(
        operations: [BathScaleWeightSummary],
        goalWeight: Double,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertStoredWeightToDisplay: (Int) -> Double,
        chartHeight: CGFloat = 265,
        lastScale: YAxisScale? = nil
    ) -> YAxisScale {

        guard !operations.isEmpty else {
            // No data — use last scale if available, otherwise show goal weight as center
            if let lastScale = lastScale {
                return lastScale
            } else {
                return createFallbackScale(goalWeight: goalWeight)
            }
        }

        let average = calculateAverage(
            operations: operations,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertStoredWeightToDisplay: convertStoredWeightToDisplay
        )

        let weightValues = extractWeightValues(
            operations: operations,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertStoredWeightToDisplay: convertStoredWeightToDisplay
        )

        guard let minValue = weightValues.min(),
              let maxValue = weightValues.max() else {
            return createFallbackScale(goalWeight: goalWeight, lastScale: lastScale)
        }

        // Generate nice Y-axis scale using best practices
        let scale = NiceScaleCalculator.generateNiceScale(
            minValue: minValue,
            maxValue: maxValue,
            goalWeight: goalWeight,
            targetTickCount: 6 // Optimal number of ticks for readability
        )

        return YAxisScale(
            min: scale.min,
            max: scale.max,
            step: scale.step,
            ticks: scale.ticks,
            domain: scale.domain,
            average: average
        )
    }

    /// Calculate average weight from operations
    private static func calculateAverage(
        operations: [BathScaleWeightSummary],
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertStoredWeightToDisplay: (Int) -> Double
    ) -> Double {
        let weightValues = extractWeightValues(
            operations: operations,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertStoredWeightToDisplay: convertStoredWeightToDisplay
        )
        guard !weightValues.isEmpty else { return 0 }
        return weightValues.reduce(0, +) / Double(weightValues.count)
    }

    /// Extract weight values from operations
    private static func extractWeightValues(
        operations: [BathScaleWeightSummary],
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertStoredWeightToDisplay: (Int) -> Double
    ) -> [Double] {
        return operations.map { summary in
            if isWeightlessMode {
                guard let anchor = anchorWeight else { return 0 }
                let converted = convertStoredWeightToDisplay(Int(summary.weight))
                return converted - anchor
            } else {
                return convertStoredWeightToDisplay(Int(summary.weight))
            }
        }
    }

    /// Create fallback scale when no data is available
    private static func createFallbackScale(goalWeight: Double, lastScale: YAxisScale? = nil) -> YAxisScale {
        if let last = lastScale {
            return last
        } else {
            // Use nice scale for fallback too
            let scale = NiceScaleCalculator.generateNiceScale(
                minValue: goalWeight - 5,
                maxValue: goalWeight + 5,
                goalWeight: goalWeight,
                targetTickCount: 5
            )
            return YAxisScale(
                min: scale.min,
                max: scale.max,
                step: scale.step,
                ticks: scale.ticks,
                domain: scale.domain,
                average: goalWeight
            )
        }
    }
}

// MARK: - NiceScaleCalculator

/// Implements the "nice numbers" algorithm for Y-axis tick calculation
/// Based on data visualization best practices and research
fileprivate struct NiceScaleCalculator {
    
    /// Nice numbers that are easy to read and understand
    /// Following the sequence: 1, 2, 5, 10, 15, 20, 25, 40, 50, 100, etc.
    private static let niceNumbers: [Double] = [1, 2, 5, 10, 15, 20, 25, 40, 50, 100]
    
    /// Generate a nice Y-axis scale with optimal tick values
    static func generateNiceScale(
        minValue: Double,
        maxValue: Double,
        goalWeight: Double,
        targetTickCount: Int = 6
    ) -> (min: Double, max: Double, step: Double, ticks: [Double], domain: ClosedRange<Double>) {
        
        // Include goal weight in the data range
        let dataMin = min(minValue, goalWeight)
        let dataMax = max(maxValue, goalWeight)
        
        // Calculate the raw range
        let rawRange = dataMax - dataMin
        
        // Ensure minimum range for visual clarity
        let minimumRange = max(rawRange, 10.0)
        
        // Add padding for visual breathing room (10% on each side)
        let padding = minimumRange * 0.1
        let paddedMin = dataMin - padding
        let paddedMax = dataMax + padding
        
        // Calculate the optimal step size
        let step = calculateOptimalStep(
            range: paddedMax - paddedMin,
            targetTickCount: targetTickCount
        )
        
        // Generate nice min and max values
        let niceMin = floor(paddedMin / step) * step
        let niceMax = ceil(paddedMax / step) * step
        
        // Ensure goal weight is included in the range
        let finalMin = min(niceMin, floor(goalWeight / step) * step)
        let finalMax = max(niceMax, ceil(goalWeight / step) * step)
        
        // Generate ticks
        var ticks: [Double] = []
        var currentTick = finalMin
        
        while currentTick <= finalMax + 0.001 { // Small epsilon for floating point comparison
            ticks.append(currentTick)
            currentTick += step
        }
        
        // Ensure we have a reasonable number of ticks (4-8)
        if ticks.count < 4 {
            // Add more ticks by reducing step
            let smallerStep = step / 2
            ticks = []
            currentTick = finalMin
            while currentTick <= finalMax + 0.001 {
                ticks.append(currentTick)
                currentTick += smallerStep
            }
        } else if ticks.count > 8 {
            // Reduce ticks by increasing step
            let largerStep = step * 2
            ticks = []
            currentTick = finalMin
            while currentTick <= finalMax + 0.001 {
                ticks.append(currentTick)
                currentTick += largerStep
            }
        }
        
        return (
            min: finalMin,
            max: finalMax,
            step: step,
            ticks: ticks,
            domain: finalMin...finalMax
        )
    }
    
    /// Calculate optimal step size using nice numbers
    private static func calculateOptimalStep(range: Double, targetTickCount: Int) -> Double {
        // Calculate rough step
        let roughStep = range / Double(targetTickCount - 1)
        
        // Find the magnitude (power of 10)
        let magnitude = pow(10.0, floor(log10(roughStep)))
        
        // Normalize the rough step to 0-1 range
        let normalizedStep = roughStep / magnitude
        
        // Find the closest nice number
        let closestNiceNumber = niceNumbers.min { abs($0 - normalizedStep) < abs($1 - normalizedStep) } ?? 1.0
        
        // Calculate the final step
        let step = closestNiceNumber * magnitude
        
        return step
    }
}

// MARK: - Legacy YAxisHelper (kept for reference, not used)

fileprivate struct YAxisHelper {
    /// Generate Y-axis with Apple Health–style domain and ticks
    static func generateYAxis(
        minValue: Double,
        maxValue: Double,
        goalWeight: Double,
        desiredLabelCount: Int = 10
    ) -> (min: Double, max: Double, step: Double, ticks: [Double], domain: ClosedRange<Double>) {

        // Include goalWeight in data range
        var dataMin = min(minValue, goalWeight)
        var dataMax = max(maxValue, goalWeight)

        // Ensure minimum range (e.g. at least 10 units)
        let rawRange = dataMax - dataMin
        let minimumRange: Double = 10
        let expandedRange = max(rawRange, minimumRange)

        // Add symmetrical padding (10% each side)
        let padding = expandedRange * 0.1
        dataMin -= padding
        dataMax += padding

        // Snap min/max to rounded 5s
        var niceMin = floor(dataMin / 2) * 2
        var niceMax = ceil(dataMax / 2) * 2

        // Ensure goal is inside
        niceMin = min(niceMin, floor(goalWeight / 2) * 2)
        niceMax = max(niceMax, ceil(goalWeight / 2) * 2)

        // Calculate desired step
        let totalRange = niceMax - niceMin
        let roughStep = totalRange / Double(desiredLabelCount - 1)

        // Snap step to nice values
        let step: Double
        if roughStep <= 2 { step = 2 }
        else if roughStep <= 5 { step = 5 }
        else if roughStep <= 10 { step = 10 }
        else if roughStep <= 20 { step = 20 }
        else { step = 25 }

        // Generate ticks
        var ticks: [Double] = []
        var tick = niceMin
        while tick <= niceMax + 0.1 {
            ticks.append(tick)
            tick += step
        }

        return (niceMin, niceMax, step, ticks, niceMin...niceMax)
    }
}
