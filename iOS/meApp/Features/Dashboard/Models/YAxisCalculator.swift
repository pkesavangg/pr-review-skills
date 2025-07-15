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
    ///   - minTickSpacing: Minimum spacing between ticks
    ///   - lastScale: Previous YAxisScale to use as fallback when no data
    /// - Returns: YAxisScale with calculated domain and ticks
    static func calculateYAxis(
        operations: [BathScaleWeightSummary],
        goalWeight: Double,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertStoredWeightToDisplay: (Int) -> Double,
        chartHeight: CGFloat = 265,
        minTickSpacing: CGFloat = 40,
        lastScale: YAxisScale? = nil
    ) -> YAxisScale {

        // Calculate optimal number of ticks based on chart height
        let optimalTickCount = max(3, min(8, Int(chartHeight / minTickSpacing)))
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
        print("domain weightValues: \(weightValues)")

        guard let minValue = weightValues.min(),
              let maxValue = weightValues.max() else {
            return createFallbackScale(goalWeight: goalWeight, lastScale: lastScale)
        }

        let scale = YAxisHelper.generateYAxis(
            minValue: minValue,
            maxValue: maxValue,
            goalWeight: goalWeight,
            desiredLabelCount: optimalTickCount
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
            let labels = [goalWeight - 5, goalWeight, goalWeight + 5]
            return YAxisScale(
                min: labels.first!,
                max: labels.last!,
                step: 5,
                ticks: labels,
                domain: labels.first!...labels.last!,
                average: goalWeight
            )
        }
    }
}

// MARK: - YAxisHelper

fileprivate struct YAxisHelper {

    /// Generate Y-axis with Apple Health–style domain and ticks
    static func generateYAxis(
        minValue: Double,
        maxValue: Double,
        goalWeight: Double,
        desiredLabelCount: Int = 5
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
        var niceMin = floor(dataMin / 5) * 5
        var niceMax = ceil(dataMax / 5) * 5

        // Ensure goal is inside
        niceMin = min(niceMin, floor(goalWeight / 5) * 5)
        niceMax = max(niceMax, ceil(goalWeight / 5) * 5)

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
