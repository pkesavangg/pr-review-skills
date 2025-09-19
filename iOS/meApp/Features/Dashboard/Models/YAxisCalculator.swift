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
            // No data — use last scale if available, otherwise use a goal-agnostic default fallback
            if let lastScale = lastScale {
                // Allow negative ticks only in weightless mode
                return isWeightlessMode
                ? lastScale
                : sanitizeToNonNegativeUniformTicks(scale: lastScale, desiredTickCount: max(2, lastScale.ticks.count))
            } else {
                let fallback = createFallbackScale(goalWeight: goalWeight)
                // Fallback is already non-negative; keep consistent behavior
                return isWeightlessMode
                ? fallback
                : sanitizeToNonNegativeUniformTicks(scale: fallback, desiredTickCount: max(2, fallback.ticks.count))
            }
        }

        // Handle small datasets (1-2 entries) with special logic
        if operations.count <= 2 {
            let small = handleSmallDataset(
                operations: operations,
                goalWeight: goalWeight,
                isWeightlessMode: isWeightlessMode,
                anchorWeight: anchorWeight,
                convertStoredWeightToDisplay: convertStoredWeightToDisplay,
                lastScale: lastScale
            )
            // Allow negative ticks only in weightless mode
            return isWeightlessMode
            ? small
            : sanitizeToNonNegativeUniformTicks(scale: small, desiredTickCount: max(3, small.ticks.count))
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
        
        // Apply edge buffer so top/bottom data points don't touch chart edges
        let buffered = applyEdgeBufferToTicks(
            dataMin: minValue,
            dataMax: maxValue,
            step: scale.step,
            ticks: scale.ticks
        )

        // Align domain to adjusted tick range so plot bounds match horizontal rules
        let domainMin = buffered.ticks.first ?? scale.min
        let domainMax = buffered.ticks.last ?? scale.max
        let initial = YAxisScale(
            min: domainMin,
            max: domainMax,
            step: buffered.step,
            ticks: buffered.ticks,
            domain: domainMin...domainMax,
            average: average
        )

        // Allow negative ticks only in weightless mode
        return isWeightlessMode
        ? initial
        : sanitizeToNonNegativeUniformTicks(scale: initial, desiredTickCount: max(3, initial.ticks.count))
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

        // Ignore goal weight; keep domain purely data/padding based
        let finalMin: Double = scaleMin
        let finalMax: Double = scaleMax


        // Create simple ticks with appropriate steps for small datasets
        var (adjustedStep, adjustedTicks) = enforceTickLimits(
            min: finalMin,
            max: finalMax,
            initialStep: 1.0
        )

        // Apply edge buffer so points don't touch top/bottom
        let buffered = applyEdgeBufferToTicks(
            dataMin: minValue,
            dataMax: maxValue,
            step: adjustedStep,
            ticks: adjustedTicks
        )
        adjustedStep = buffered.step
        adjustedTicks = buffered.ticks

        // Align domain to tick range so plot bounds coincide with horizontal rules
        let domainMinSmall = adjustedTicks.first ?? finalMin
        let domainMaxSmall = adjustedTicks.last ?? finalMax
        return YAxisScale(
            min: domainMinSmall,
            max: domainMaxSmall,
            step: adjustedStep,
            ticks: adjustedTicks,
            domain: domainMinSmall...domainMaxSmall,
            average: average
        )
    }

    /// Enforce tick limits (min 3, max 8) by adjusting step size
    internal static func enforceTickLimits(min: Double, max: Double, initialStep: Double) -> (step: Double, ticks: [Double]) {
        var step = initialStep
        var ticks: [Double] = []

        // Generate initial ticks
        var currentTick = min
        while currentTick <= max + 0.001 {
            ticks.append(currentTick)
            currentTick += step
        }

        // Adjust if too many ticks (> 8)
        while ticks.count > 6 {
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
            // Goal-independent default fallback
            let defaultMin: Double = 0
            let defaultMax: Double = 100
            let step: Double = 25
            var ticks: [Double] = []
            var t = defaultMin
            while t <= defaultMax + 0.001 {
                ticks.append(t)
                t += step
            }
            let domainMin = ticks.first ?? defaultMin
            let domainMax = ticks.last ?? defaultMax
            return YAxisScale(
                min: defaultMin,
                max: defaultMax,
                step: step,
                ticks: ticks,
                domain: domainMin...domainMax,
                average: 0
            )
        }
    }

    /// Build a non-negative, uniform Y-axis from 0 with exactly `desiredTickCount` ticks.
    /// Ensures ticks are monotonically increasing and aligned to a "nice" step.
    private static func sanitizeToNonNegativeUniformTicks(scale: YAxisScale, desiredTickCount: Int) -> YAxisScale {
        // Only sanitize if any negative ticks or negative domain/min would be produced
        let hasNegativeTicks = scale.ticks.contains { $0 < 0 } || scale.min < 0 || scale.domain.lowerBound < 0
        guard hasNegativeTicks else { return scale }

        let count = max(2, desiredTickCount)

        // Use the existing upper bound to preserve data headroom, but clamp to non-negative
        let upperBoundCandidate = max(scale.max, scale.domain.upperBound, scale.ticks.last ?? 0)
        let upper = max(0, upperBoundCandidate)

        // Compute step so that (count - 1) * step >= upper, snapping to a nice step
        let rawStep = (upper <= 0) ? 1.0 : (upper / Double(count - 1))
        let step = pickNiceStep(atLeast: max(rawStep, 0.0001))

        // Normalize top tick to a multiple of step
        let stepsNeeded = ceil(upper / step)
        let top = max(step, stepsNeeded * step)

        // Rebuild non-negative uniform ticks from 0..top with `count` items
        var ticks: [Double] = []
        if count > 1 {
            let evenStep = top / Double(count - 1)
            for i in 0..<(count) {
                // Round each tick to nearest multiple of a small epsilon to avoid floating artifacts
                let value = Double(i) * evenStep
                ticks.append(value)
            }
        } else {
            ticks = [0]
        }

        let minVal: Double = 0
        let maxVal: Double = ticks.last ?? step
        return YAxisScale(
            min: minVal,
            max: maxVal,
            step: ticks.count > 1 ? (ticks[1] - ticks[0]) : step,
            ticks: ticks,
            domain: minVal...maxVal,
            average: scale.average
        )
    }

    /// Pick a "nice" step size that is >= threshold using the same nice numbers logic.
    private static func pickNiceStep(atLeast threshold: Double) -> Double {
        // Determine magnitude (power of 10)
        let magnitude = pow(10.0, floor(log10(max(threshold, 1e-9))))
        let normalized = threshold / magnitude

        // Same nice set as ImprovedNiceScaleCalculator
        let niceNumbers: [Double] = [1, 2, 5, 10, 15, 20, 25, 40, 50, 100]

        // Find first nice >= normalized, otherwise bump magnitude
        if let candidate = niceNumbers.first(where: { $0 >= normalized }) {
            return candidate * magnitude
        } else {
            // If none is big enough, move to next order of magnitude with the smallest nice number
            return (niceNumbers.first ?? 1) * magnitude * 10.0
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
        let range = max(dataMax - dataMin, 2.0) // Minimum 2-unit range for visibility

        // Calculate bounds with padding, but ensure we include all actual data
        let padding = range * 0.2
        let min = floor(dataMin - padding) // Start from actual data minimum
        let max = ceil(dataMax + padding)  // End at actual data maximum

        // Ignore goal weight; keep domain purely data/padding based
        let finalMin: Double = min
        let finalMax: Double = max

                // Generate ticks with appropriate step size while enforcing limits
        let initialStep = 1.0
        let (adjustedStep, adjustedTicks) = YAxisCalculator.enforceTickLimits(
            min: finalMin,
            max: finalMax,
            initialStep: initialStep
        )

        // Align domain to tick range so plot bounds coincide with horizontal rules
        let domainMin = adjustedTicks.first ?? finalMin
        let domainMax = adjustedTicks.last ?? finalMax
        return (finalMin, finalMax, adjustedStep, adjustedTicks, domainMin...domainMax)
    }

        /// Handle medium ranges (5-15 units)
    private static func handleMediumRange(dataMin: Double, dataMax: Double, goalWeight: Double) -> (min: Double, max: Double, step: Double, ticks: [Double], domain: ClosedRange<Double>) {
        // For medium ranges, use 2-unit steps
        let range = dataMax - dataMin
        let padding = range * 0.15

        let min = floor(dataMin - padding)
        let max = ceil(dataMax + padding)

        // Ignore goal weight; keep domain purely data/padding based
        let finalMin: Double = min
        let finalMax: Double = max

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

        // Ignore goal weight; keep domain purely data/padding based
        let finalMin: Double = niceMin
        let finalMax: Double = niceMax

        // Generate ticks with appropriate step size while enforcing limits
        let (adjustedStep, adjustedTicks) = YAxisCalculator.enforceTickLimits(
            min: finalMin,
            max: finalMax,
            initialStep: step
        )

        // Align domain to tick range so plot bounds coincide with horizontal rules
        let domainMin = adjustedTicks.first ?? finalMin
        let domainMax = adjustedTicks.last ?? finalMax
        return (
            min: finalMin,
            max: finalMax,
            step: adjustedStep,
            ticks: adjustedTicks,
            domain: domainMin...domainMax
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

// MARK: - Edge Buffer Helpers

extension YAxisCalculator {
    /// Ensures there is visual headroom/footroom between data extremes and the outermost ticks.
    /// Keeps domain aligned with ticks to avoid gridline overflow beyond the plot area.
    /// - Parameters:
    ///   - dataMin: Minimum actual data value in display units
    ///   - dataMax: Maximum actual data value in display units
    ///   - step: Current tick step size
    ///   - ticks: Current tick values (ascending)
    ///   - thresholdRatio: If distance between data and nearest tick is less than `step * thresholdRatio`, extend one more step
    ///   - maxTicks: Soft cap for number of ticks; will re-enforce limits after extension
    /// - Returns: Adjusted step and ticks with edge buffer applied
    static func applyEdgeBufferToTicks(
        dataMin: Double,
        dataMax: Double,
        step: Double,
        ticks: [Double],
        thresholdRatio: Double = 0.35,
        maxTicks: Int = 6
    ) -> (step: Double, ticks: [Double]) {
        guard !ticks.isEmpty else { return (step, ticks) }

        var proposedMin = ticks.first!
        var proposedMax = ticks.last!
        let proposedStep = step

        // Determine if data is too close to outer ticks
        let tooCloseToTop = (proposedMax - dataMax) < (proposedStep * thresholdRatio)
        let tooCloseToBottom = (dataMin - proposedMin) < (proposedStep * thresholdRatio)

        if tooCloseToTop { proposedMax += proposedStep }
        if tooCloseToBottom { proposedMin -= proposedStep }

        // Re-enforce tick limits and regenerate ticks uniformly
        var enforced = enforceTickLimits(min: proposedMin, max: proposedMax, initialStep: proposedStep)

        // If still too close and we haven't exceeded soft cap, try to extend once more on each side
        if let last = enforced.ticks.last, (last - dataMax) < (enforced.step * thresholdRatio), enforced.ticks.count < maxTicks {
            proposedMax = last + enforced.step
            enforced = enforceTickLimits(min: enforced.ticks.first ?? proposedMin, max: proposedMax, initialStep: enforced.step)
        }
        if let first = enforced.ticks.first, (dataMin - first) < (enforced.step * thresholdRatio), enforced.ticks.count < maxTicks {
            proposedMin = first - enforced.step
            enforced = enforceTickLimits(min: proposedMin, max: enforced.ticks.last ?? proposedMax, initialStep: enforced.step)
        }

        return (enforced.step, enforced.ticks)
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
