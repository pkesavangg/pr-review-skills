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

// MARK: - NiceTicksResult

/// Result structure for niceTicks to avoid large tuples
struct NiceTicksResult {
    let ticks: [Double]
    let step: Double
    let domain: ClosedRange<Double>
}

// MARK: - NiceScaleResult

/// Result structure for nice scale calculations to avoid large tuples
private struct NiceScaleResult {
    let min: Double
    let max: Double
    let step: Double
    let ticks: [Double]
    let domain: ClosedRange<Double>
}

// MARK: - YAxisCalculator

struct YAxisCalculator {
    // Fallback defaults for Y-axis when no data is available
    private static let fallbackMin: Double = 0
    private static let fallbackMax: Double = 100
    private static let fallbackStep: Double = 25
    private static let goalTickOffsets: [Double] = [-2, -1, 0, 1, 2]
    /// Expanded set of nice numbers used for step selection (normalized domain)
    private static let niceNumbers: [Double] = [1, 2, 4, 5, 10, 15, 20, 25, 40, 50, 100, 200]

    // Calculate Y-axis scale for chart display.
    // Parameters:
    // - operations: Array of weight summaries
    // - goalWeight: Goal weight for display
    // - isWeightlessMode: Whether weightless mode is enabled
    // - anchorWeight: Anchor weight for weightless mode
    // - convertStoredWeightToDisplay: Function to convert stored weight to display weight
    // - chartHeight: Chart height for optimal tick calculation
    // - lastScale: Previous YAxisScale to use as fallback when no data
    // Returns: YAxisScale with calculated domain and ticks
    static func calculateYAxis( // swiftlint:disable:this function_body_length
        operations: [BathScaleWeightSummary],
        goalWeight: Double?,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertStoredWeightToDisplay: (Double) -> Double,
        chartHeight: CGFloat = 265,
        lastScale: YAxisScale? = nil
    ) -> YAxisScale {
        guard !operations.isEmpty else {
            // EMPTY-STATE BEHAVIOR
            // If a goal is set (non-nil and non-zero), show goal-centric ticks; else fallback to last or default scale (ticks still present)
            if let goalWeight = goalWeight {
                return buildGoalCentricFallback(goalWeight: goalWeight)
            }
            if let last = lastScale {
                return isWeightlessMode
                ? last
                : sanitizeToNonNegativeUniformTicks(scale: last, desiredTickCount: max(2, last.ticks.count))
            } else {
                let fallback = createFallbackScale(goalWeight: goalWeight)
                return isWeightlessMode
                ? fallback
                : sanitizeToNonNegativeUniformTicks(scale: fallback, desiredTickCount: max(2, fallback.ticks.count))
            }
        }

        // Handle small datasets (1-2 entries) with special logic
        if operations.count <= 2 {
            let small = handleSmallDataset(
                operations: operations,
                goalWeight: goalWeight ?? 0,
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
            goalWeight: goalWeight ?? 0,
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
        let finalScale = isWeightlessMode
        ? initial
        : sanitizeToNonNegativeUniformTicks(scale: initial, desiredTickCount: max(3, initial.ticks.count))

        return finalScale
    }

    /// Compute evenly spaced, human-friendly ticks using classic 1–2–5 × 10^n steps.
    /// Returns ticks, step, and the snapped domain [niceMin, niceMax].
    internal static func niceTicks( // Method cannot be declared internal because its result uses a private type
        min: Double,
        max: Double,
        desiredTickCount: Int
    ) -> NiceTicksResult {
        let range = max - min
        guard range.isFinite, range > 0, desiredTickCount > 1 else {
            let lo = Swift.min(min, max)
            let hi = Swift.max(min, max)
            return NiceTicksResult(ticks: [lo, hi], step: Swift.max(hi - lo, 1.0), domain: lo...hi)
        }

        let rawInterval = range / Double(desiredTickCount - 1)
        // nearest power of 10
        let magnitude = pow(10, floor(log10(Swift.max(rawInterval, AppConstants.Precision.doubleEqualityEpsilon))))
        let residual = rawInterval / magnitude

        let niceResidual: Double
        if residual <= 1 {
            niceResidual = 1
        } else if residual <= 2 {
            niceResidual = 2
        } else if residual <= 5 {
            niceResidual = 5
        } else {
            niceResidual = 10
        }

        // Ensure minimum step of 1.0 to avoid decimal ticks that look like duplicates
        let step = Swift.max(niceResidual * magnitude, 1.0)

        // Snap bounds to multiples of step
        let niceMin = floor(min / step) * step
        let niceMax = ceil(max / step) * step

        // Build ticks
        var ticks: [Double] = []
        var tick = niceMin
        // Small epsilon to ensure inclusion of upper bound
        while tick <= niceMax + 1e-9 {
            // Round to avoid floating artifacts at 10^n boundaries
            let rounded = (tick / step).rounded() * step
            ticks.append(rounded)
            tick += step
        }

        // De-dup and sort for safety
        let deduped = Array(Set(ticks)).sorted()
        let domain = (deduped.first ?? niceMin)...(deduped.last ?? niceMax)
        let actualStep = deduped.count > 1 ? (deduped[1] - deduped[0]) : step
        return NiceTicksResult(ticks: deduped, step: actualStep, domain: domain)
    }

    /// Calculate average weight from operations
    private static func calculateAverage(
        operations: [BathScaleWeightSummary],
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertStoredWeightToDisplay: (Double) -> Double
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
        convertStoredWeightToDisplay: (Double) -> Double
    ) -> [Double] {
        return operations.map { summary in
            if isWeightlessMode {
                guard let anchor = anchorWeight else { return 0 }
                let converted = convertStoredWeightToDisplay(summary.weight)
                return converted - anchor
            } else {
                return convertStoredWeightToDisplay(summary.weight)
            }
        }
    }

        /// Handle small datasets (1-2 entries) with special Y-axis logic
    private static func handleSmallDataset(
        operations: [BathScaleWeightSummary],
        goalWeight: Double,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertStoredWeightToDisplay: (Double) -> Double,
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

        // For small datasets, create a tight scale with minimal padding
        // Just enough to ensure data points don't touch the edges
        let range = maxValue - minValue
        let padding = max(range * 0.2, 0.3) // 20% padding or minimum 0.3 units

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

    // Enforce tick limits (min 3, max 8) by adjusting step size
    internal static func enforceTickLimits(min: Double, max: Double, initialStep: Double) -> (step: Double, ticks: [Double]) { // swiftlint:disable:this function_body_length
        // Helper to snap values to the nearest nice multiple of step
        @inline(__always) func snapDown(_ value: Double, step: Double) -> Double {
            guard step > 0 else { return value }
            return floor(value / step) * step
        }
        @inline(__always) func snapUp(_ value: Double, step: Double) -> Double {
            guard step > 0 else { return value }
            return ceil(value / step) * step
        }
        @inline(__always) func buildTicks(min: Double, max: Double, step: Double) -> [Double] {
            guard step > 0 else { return [min, max] }
            var ticks: [Double] = []
            var start = snapDown(min, step: step)
            let end = snapUp(max, step: step)
            // Guard against degenerate ranges
            if end < start { return [min, max] }
            while start <= end + 0.0001 {
                // Round to avoid floating artifacts
                let rounded = (start / step).rounded() * step
                ticks.append(rounded)
                start += step
            }
            return Array(Set(ticks)).sorted()
        }

        // Ensure minimum step of 1.0 to avoid decimal ticks that look like duplicates
        var step = Swift.max(initialStep, 1.0)
        let snappedMin = floor(min / step) * step
        let snappedMax = ceil(max / step) * step
        var ticks = buildTicks(min: snappedMin, max: snappedMax, step: step)

         // Adjust if too many ticks (> 8)
        while ticks.count > 6 {
            step = pickNiceStep(atLeast: step * 1.999)
            let sMin = floor(min / step) * step
            let sMax = ceil(max / step) * step
            ticks = buildTicks(min: sMin, max: sMax, step: step)
        }

        // Adjust if too few ticks (< 3) - but don't go below step of 1.0
        while ticks.count < 3 && step > 1.0 {
            step = pickNiceStepAtMost(step / 2.001)
            let sMin = floor(min / step) * step
            let sMax = ceil(max / step) * step
            ticks = buildTicks(min: sMin, max: sMax, step: step)
        }

        // Final guard: ensure uniform spacing (all diffs equal within epsilon)
        if ticks.count >= 3 {
            let diffs = zip(ticks.dropFirst(), ticks).map { $0 - $1 }
            let mean = diffs.reduce(0, +) / Double(diffs.count)
            let nonUniform = diffs.contains { abs($0 - mean) > (Swift.max(0.001, step * 0.05)) }
            if nonUniform {
                // Force rebuild using the computed mean as step, snapped to a nice step
                let rng = (ticks.last ?? snappedMax) - (ticks.first ?? snappedMin)
                let snappedStep = YAxisCalculator.calculateOptimalStep(
                    range: rng,
                    targetTickCount: Swift.max(3, Swift.min(6, ticks.count))
                )
                // Ensure minimum step of 1.0 to avoid decimal ticks
                step = Swift.max(snappedStep, 1.0)
                let sMin = floor(min / step) * step
                let sMax = ceil(max / step) * step
                ticks = buildTicks(min: sMin, max: sMax, step: step)
            }
        }

        return (step, ticks)
    }

    /// Create fallback scale when no data is available
    private static func createFallbackScale(goalWeight: Double?, lastScale: YAxisScale? = nil) -> YAxisScale {
        if let last = lastScale {
            return last
        } else {
            // Goal-independent default fallback
            var ticks: [Double] = []
            var tickValue = fallbackMin
            while tickValue <= fallbackMax + 0.001 {
                ticks.append(tickValue)
                tickValue += fallbackStep
            }
            let domainMin = ticks.first ?? fallbackMin
            let domainMax = ticks.last ?? fallbackMax
            return YAxisScale(
                min: fallbackMin,
                max: fallbackMax,
                step: fallbackStep,
                ticks: (goalWeight != nil && (goalWeight ?? 0) > 0)
                    ? buildGoalCentricFallback(goalWeight: goalWeight ?? 0).ticks
                    : ticks,
                domain: domainMin...domainMax,
                average: (fallbackMin + fallbackMax) / 2
            )
        }
    }

    /// Builds a symmetric goal-centric fallback scale with 4–6 ticks centered on goal.
    /// Examples for goal 178 with 5-unit step: 165, 170, 175, 180, 185, 190 (depending on range).
    static func buildGoalCentricFallback(goalWeight: Double) -> YAxisScale {
        // Pick a nice step based on goal magnitude (prefer 5 for lbs, 2 for tight ranges)
        let stepCandidates: [Double] = [2, 5, 10]
        let step = stepCandidates.first { goalWeight / $0 > 20 } ?? 5

        // Center around nearest multiple of step
        let nearest = round(goalWeight / step) * step
        let ticks: [Double] = goalTickOffsets.map { nearest + (Double($0) * step) }
        let domainMin = ticks.first ?? max(0, nearest - 2 * step)
        let domainMax = ticks.last ?? nearest + 2 * step
        return YAxisScale(
            min: domainMin,
            max: domainMax,
            step: step,
            ticks: ticks,
            domain: domainMin...domainMax,
            average: goalWeight
        )
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

        let tickResult = niceTicks(min: 0, max: upper, desiredTickCount: count)
        let ticks = tickResult.ticks
        let step = tickResult.step

        let minVal: Double = 0
        let maxVal: Double = ticks.last ?? step
        return YAxisScale(
            min: minVal,
            max: maxVal,
            step: step,
            ticks: ticks,
            domain: minVal...maxVal,
            average: scale.average
        )
    }

    /// Pick a "nice" step size that is >= threshold using the same nice numbers logic.
    static func pickNiceStep(atLeast threshold: Double) -> Double {
        // Determine magnitude (power of 10)
        let magnitude = pow(10.0, floor(log10(max(threshold, AppConstants.Precision.doubleEqualityEpsilon))))
        let normalized = threshold / magnitude
        // Use expanded nice numbers
        let nice = niceNumbers.first { $0 >= normalized } ?? niceNumbers.last ?? 1.0
        // Ensure minimum step of 1.0 to avoid decimal ticks
        return Swift.max(nice * magnitude, 1.0)
    }

    /// Calculate optimal step size using the expanded nice-number set.
    static func calculateOptimalStep(range: Double, targetTickCount: Int) -> Double {
        guard targetTickCount > 1 else { return Swift.max(range, 1.0) }
        let rough = max(range / Double(targetTickCount - 1), 0.0001)
        let magnitude = pow(10.0, floor(log10(rough)))
        let normalized = rough / magnitude
        let nice = niceNumbers.first { $0 >= normalized } ?? niceNumbers.last ?? 1.0
        return Swift.max(nice * magnitude, 1.0)
    }

    /// Pick the largest nice step <= threshold using classic nice set {1,2,5,10} × 10^k
    private static func pickNiceStepAtMost(_ threshold: Double) -> Double {
        let minEps = AppConstants.Precision.doubleEqualityEpsilon
        let thresholdValue = Swift.max(threshold, minEps)
        let magnitude = pow(10.0, floor(log10(thresholdValue)))
        let normalized = thresholdValue / magnitude
        let reversedNice = niceNumbers.sorted(by: >)
        let candidate = reversedNice.first { $0 <= normalized } ?? 1.0
        return Swift.max(candidate * magnitude, 1.0)
    }
}

// MARK: - ImprovedNiceScaleCalculator

/// Improved algorithm for Y-axis tick calculation optimized for gradual weight changes
/// Handles small ranges better and ensures unique, non-decimal tick values
private struct ImprovedNiceScaleCalculator {

    /// Nice numbers optimized for weight display (avoiding decimals)
    private static let niceNumbers: [Double] = [1, 2, 4, 5, 10, 15, 20, 25, 40, 50, 100, 200]

    /// Generate a nice Y-axis scale with optimal tick values for gradual changes
    static func generateNiceScale(
        minValue: Double,
        maxValue: Double,
        goalWeight: Double,
        targetTickCount: Int = 6
    ) -> NiceScaleResult {
        let dataMin = Swift.min(minValue, maxValue)
        let dataMax = Swift.max(minValue, maxValue)
        let desired = Swift.max(3, Swift.min(6, targetTickCount))
        let result = YAxisCalculator.niceTicks(min: dataMin, max: dataMax, desiredTickCount: desired)
        return NiceScaleResult(
            min: result.domain.lowerBound,
            max: result.domain.upperBound,
            step: result.step,
            ticks: result.ticks,
            domain: result.domain
        )
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
        guard let firstTick = ticks.first, let lastTick = ticks.last else { return (step, ticks) }
        var proposedMin = firstTick
        var proposedMax = lastTick
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
