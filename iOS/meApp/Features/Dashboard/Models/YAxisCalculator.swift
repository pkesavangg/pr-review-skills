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

        // Handle small datasets (1-2 entries) with special logic
        if operations.count <= 2 {
            return handleSmallDataset(
                operations: operations,
                goalWeight: goalWeight,
                isWeightlessMode: isWeightlessMode,
                anchorWeight: anchorWeight,
                convertStoredWeightToDisplay: convertStoredWeightToDisplay,
                lastScale: lastScale
            )
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

        // Generate nice Y-axis scale using improved algorithm for gradual changes
        let scale = ImprovedNiceScaleCalculator.generateNiceScale(
            minValue: minValue,
            maxValue: maxValue,
            goalWeight: goalWeight,
            targetTickCount: 4 // Reduced tick count for cleaner graphs
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

        /// Handle small datasets (1-2 entries) with special Y-axis logic
    private static func handleSmallDataset(
        operations: [BathScaleWeightSummary],
        goalWeight: Double,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertStoredWeightToDisplay: (Int) -> Double,
        lastScale: YAxisScale? = nil
    ) -> YAxisScale {
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

        let average = weightValues.reduce(0, +) / Double(weightValues.count)

        // For small datasets, create a simple scale with reasonable padding
        let range = maxValue - minValue
        let padding = max(range * 0.3, 2.0) // 30% padding or minimum 2 units

        let scaleMin = floor(minValue - padding)
        let scaleMax = ceil(maxValue + padding)

        // Only include goal weight if it's within a reasonable range of the actual data
        let dataCenter = (minValue + maxValue) / 2
        let dataRange = maxValue - minValue
        let reasonableGoalRange = dataRange * 2 // Goal should be within 2x the data range

        let finalMin: Double
        let finalMax: Double

        if abs(goalWeight - dataCenter) <= reasonableGoalRange {
            // Goal weight is reasonable, include it
            finalMin = Swift.min(scaleMin, floor(goalWeight - 2))
            finalMax = Swift.max(scaleMax, ceil(goalWeight + 2))
        } else {
            // Goal weight is too far from data, ignore it
            finalMin = scaleMin
            finalMax = scaleMax
        }


        // Create simple ticks with appropriate steps for small datasets
        let (adjustedStep, adjustedTicks) = enforceTickLimits(
            min: finalMin,
            max: finalMax,
            initialStep: 1.0
        )

        return YAxisScale(
            min: finalMin,
            max: finalMax,
            step: adjustedStep,
            ticks: adjustedTicks,
            domain: finalMin...finalMax,
            average: average
        )
    }

    /// Enforce tick limits (min 3, max 8) by adjusting step size
    internal static func enforceTickLimits(min: Double, max: Double, initialStep: Double) -> (step: Double, ticks: [Double]) {
        let range = max - min
        var step = initialStep
        var ticks: [Double] = []

        // Generate initial ticks
        var currentTick = min
        while currentTick <= max + 0.001 {
            ticks.append(currentTick)
            currentTick += step
        }

        // Adjust if too many ticks (> 8)
        while ticks.count > 8 {
            step *= 2 // Double the step size
            ticks = []
            currentTick = min
            while currentTick <= max + 0.001 {
                ticks.append(currentTick)
                currentTick += step
            }
        }

        // Adjust if too few ticks (< 3)
        while ticks.count < 3 && step > 0.1 {
            step /= 2 // Halve the step size
            ticks = []
            currentTick = min
            while currentTick <= max + 0.001 {
                ticks.append(currentTick)
                currentTick += step
            }
        }

        // Ensure unique ticks and sort
        ticks = Array(Set(ticks)).sorted()

        return (step, ticks)
    }

    /// Create fallback scale when no data is available
    private static func createFallbackScale(goalWeight: Double, lastScale: YAxisScale? = nil) -> YAxisScale {
        if let last = lastScale {
            return last
        } else {
                    // Use improved scale for fallback too
        let scale = ImprovedNiceScaleCalculator.generateNiceScale(
            minValue: goalWeight - 5,
            maxValue: goalWeight + 5,
            goalWeight: goalWeight,
            targetTickCount: 3
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

// MARK: - ImprovedNiceScaleCalculator

/// Improved algorithm for Y-axis tick calculation optimized for gradual weight changes
/// Handles small ranges better and ensures unique, non-decimal tick values
fileprivate struct ImprovedNiceScaleCalculator {

    /// Nice numbers optimized for weight display (avoiding decimals)
    private static let niceNumbers: [Double] = [1, 2, 5, 10, 15, 20, 25, 40, 50, 100]

    /// Generate a nice Y-axis scale with optimal tick values for gradual changes
    static func generateNiceScale(
        minValue: Double,
        maxValue: Double,
        goalWeight: Double,
        targetTickCount: Int = 6
    ) -> (min: Double, max: Double, step: Double, ticks: [Double], domain: ClosedRange<Double>) {

        // Ensure we include the full range of actual data values
        let actualMin = floor(minValue) // Include the full range of data
        let actualMax = ceil(maxValue)  // Include the full range of data

        // Calculate the raw range of actual data
        let rawRange = actualMax - actualMin

        // Handle very small ranges (gradual weight changes)
        if rawRange < 5.0 {
            return handleSmallRange(dataMin: actualMin, dataMax: actualMax, goalWeight: goalWeight)
        }

        // Handle small ranges (5-15 units)
        if rawRange < 15.0 {
            return handleMediumRange(dataMin: actualMin, dataMax: actualMax, goalWeight: goalWeight)
        }

        // Handle normal ranges
        return handleNormalRange(dataMin: actualMin, dataMax: actualMax, goalWeight: goalWeight, targetTickCount: targetTickCount)
    }

            /// Handle very small ranges (gradual weight changes)
    private static func handleSmallRange(dataMin: Double, dataMax: Double, goalWeight: Double) -> (min: Double, max: Double, step: Double, ticks: [Double], domain: ClosedRange<Double>) {
        // For very small ranges, use 1-unit steps and ensure we show the trend
        let center = (dataMin + dataMax) / 2
        let range = max(dataMax - dataMin, 2.0) // Minimum 2-unit range for visibility

        // Calculate bounds with padding, but ensure we include all actual data
        let padding = range * 0.2
        let min = floor(dataMin - padding) // Start from actual data minimum
        let max = ceil(dataMax + padding)  // End at actual data maximum

        // Only include goal weight if it's within a reasonable range of the actual data
        let dataCenter = (dataMin + dataMax) / 2
        let dataRange = dataMax - dataMin
        let reasonableGoalRange = dataRange * 2 // Goal should be within 2x the data range

        let finalMin: Double
        let finalMax: Double

        if abs(goalWeight - dataCenter) <= reasonableGoalRange {
            // Goal weight is reasonable, include it
            finalMin = Swift.min(min, floor(goalWeight))
            finalMax = Swift.max(max, ceil(goalWeight))
        } else {
            // Goal weight is too far from data, ignore it
            finalMin = min
            finalMax = max
        }

                // Generate ticks with appropriate step size while enforcing limits
        let initialStep = 1.0
        let (adjustedStep, adjustedTicks) = YAxisCalculator.enforceTickLimits(
            min: finalMin,
            max: finalMax,
            initialStep: initialStep
        )

        return (finalMin, finalMax, adjustedStep, adjustedTicks, finalMin...finalMax)
    }

        /// Handle medium ranges (5-15 units)
    private static func handleMediumRange(dataMin: Double, dataMax: Double, goalWeight: Double) -> (min: Double, max: Double, step: Double, ticks: [Double], domain: ClosedRange<Double>) {
        // For medium ranges, use 2-unit steps
        let range = dataMax - dataMin
        let padding = range * 0.15

        let min = floor(dataMin - padding)
        let max = ceil(dataMax + padding)

        // Only include goal weight if it's within a reasonable range of the actual data
        let dataCenter = (dataMin + dataMax) / 2
        let dataRange = dataMax - dataMin
        let reasonableGoalRange = dataRange * 2 // Goal should be within 2x the data range

        let finalMin: Double
        let finalMax: Double

        if abs(goalWeight - dataCenter) <= reasonableGoalRange {
            // Goal weight is reasonable, include it
            finalMin = Swift.min(min, floor(goalWeight / 2) * 2, floor(dataMin))
            finalMax = Swift.max(max, ceil(goalWeight / 2) * 2, ceil(dataMax))
        } else {
            // Goal weight is too far from data, ignore it
            finalMin = min
            finalMax = max
        }

        // Generate ticks with appropriate step size while enforcing limits
        let (adjustedStep, adjustedTicks) = YAxisCalculator.enforceTickLimits(
            min: finalMin,
            max: finalMax,
            initialStep: 2.0
        )

        return (finalMin, finalMax, adjustedStep, adjustedTicks, finalMin...finalMax)
    }

    /// Handle normal ranges (15+ units)
    private static func handleNormalRange(dataMin: Double, dataMax: Double, goalWeight: Double, targetTickCount: Int) -> (min: Double, max: Double, step: Double, ticks: [Double], domain: ClosedRange<Double>) {
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

        // Only include goal weight if it's within a reasonable range of the actual data
        let dataCenter = (dataMin + dataMax) / 2
        let dataRange = dataMax - dataMin
        let reasonableGoalRange = dataRange * 2 // Goal should be within 2x the data range

        let finalMin: Double
        let finalMax: Double

        if abs(goalWeight - dataCenter) <= reasonableGoalRange {
            // Goal weight is reasonable, include it
            finalMin = min(niceMin, floor(goalWeight / step) * step, floor(dataMin))
            finalMax = max(niceMax, ceil(goalWeight / step) * step, ceil(dataMax))
        } else {
            // Goal weight is too far from data, ignore it
            finalMin = niceMin
            finalMax = niceMax
        }

        // Generate ticks with appropriate step size while enforcing limits
        let (adjustedStep, adjustedTicks) = YAxisCalculator.enforceTickLimits(
            min: finalMin,
            max: finalMax,
            initialStep: step
        )

        return (
            min: finalMin,
            max: finalMax,
            step: adjustedStep,
            ticks: adjustedTicks,
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
