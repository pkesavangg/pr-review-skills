 
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
    /// - Returns: YAxisScale with calculated domain and ticks
    static func calculateYAxis(
        operations: [BathScaleWeightSummary],
        goalWeight: Double,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertStoredWeightToDisplay: (Int) -> Double,
        chartHeight: CGFloat = 265,
        minTickSpacing: CGFloat = 40
    ) -> YAxisScale {
        
        // Calculate optimal number of ticks based on chart height
        let optimalTickCount = max(3, min(8, Int(chartHeight / minTickSpacing)))
        
        guard !operations.isEmpty else {
            // No data — show goal weight as center
            let goal = goalWeight
            let labels = [goal - 1, goal, goal + 1, goal + 2]
            return YAxisScale(
                min: labels.first!,
                max: labels.last!,
                step: 1,
                ticks: labels,
                domain: labels.first!...labels.last!,
                average: goalWeight
            )
        }

        let average = calculateAverage(operations: operations, isWeightlessMode: isWeightlessMode, anchorWeight: anchorWeight, convertStoredWeightToDisplay: convertStoredWeightToDisplay)
        let weightValues = extractWeightValues(operations: operations, isWeightlessMode: isWeightlessMode, anchorWeight: anchorWeight, convertStoredWeightToDisplay: convertStoredWeightToDisplay)
        
        guard let minValue = weightValues.min(),
              let maxValue = weightValues.max() else {
            return createFallbackScale(goalWeight: goalWeight)
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
        let weightValues = extractWeightValues(operations: operations, isWeightlessMode: isWeightlessMode, anchorWeight: anchorWeight, convertStoredWeightToDisplay: convertStoredWeightToDisplay)
        guard !weightValues.isEmpty else { return 0 }
        if weightValues.count == 1 {
            return weightValues[0]
        }
        return weightValues.reduce(0, +) / Double(weightValues.count)
    }
    
    /// Extract weight values from operations
    private static func extractWeightValues(
        operations: [BathScaleWeightSummary],
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertStoredWeightToDisplay: (Int) -> Double
    ) -> [Double] {
        return operations.map { summary -> Double in
            if isWeightlessMode {
                guard let anchorWeight = anchorWeight else { return 0 }
                // Convert stored weight to display and calculate difference
                let currentWeight = convertStoredWeightToDisplay(Int(summary.weight))
                return currentWeight - anchorWeight
            } else {
                return convertStoredWeightToDisplay(Int(summary.weight))
            }
        }
    }
    
    /// Create fallback scale when no data is available
    private static func createFallbackScale(goalWeight: Double) -> YAxisScale {
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

// MARK: - YAxisHelper

fileprivate struct YAxisHelper {
    
    /// Generate Y-axis with optimal domain and ticks
    /// - Parameters:
    ///   - minValue: Minimum weight value
    ///   - maxValue: Maximum weight value
    ///   - goalWeight: Goal weight to include in range
    ///   - desiredLabelCount: Desired number of tick labels
    /// - Returns: Tuple with min, max, step, ticks, and domain
    static func generateYAxis(
        minValue: Double,
        maxValue: Double,
        goalWeight: Double,
        desiredLabelCount: Int = 4
    ) -> (min: Double, max: Double, step: Double, ticks: [Double], domain: ClosedRange<Double>) {
        
        // Include goal weight in the range calculation
        let allValues = [minValue, maxValue, goalWeight]
        let dataMin = allValues.min() ?? minValue
        let dataMax = allValues.max() ?? maxValue
        
        // Add padding for better visibility
        let range = dataMax - dataMin
        let padding = range * 0.1 // 10% padding
        
        let paddedMin = dataMin - padding
        let paddedMax = dataMax + padding
        
        // Calculate appropriate step size
        let totalRange = paddedMax - paddedMin
        let idealStep = totalRange / Double(desiredLabelCount - 1)
        
        // Find the best step size from common increments (whole numbers only)
        let stepSizes: [Double] = [1, 2, 5, 10, 20, 25, 50, 100]
        var bestStep = 1.0
        var bestFit = Double.infinity
        
        for step in stepSizes {
            let fit = abs(step - idealStep)
            if fit < bestFit {
                bestFit = fit
                bestStep = step
            }
        }
        
        // Ensure we have at least 3 ticks and at most 6 ticks
        let estimatedTickCount = totalRange / bestStep
        if estimatedTickCount < 3 {
            // If too few ticks, try smaller steps
            for step in stepSizes.reversed() {
                if step < bestStep && (totalRange / step) >= 3 {
                    bestStep = step
                    break
                }
            }
        } else if estimatedTickCount > 6 {
            // If too many ticks, try larger steps
            for step in stepSizes {
                if step > bestStep && (totalRange / step) <= 6 {
                    bestStep = step
                    break
                }
            }
        }
        
        // For very small ranges, ensure we have enough granularity (minimum step of 1)
        if totalRange < 5 && bestStep > totalRange / 2 {
            bestStep = max(1.0, totalRange / 3)
        }
        
        // Calculate start and end points to ensure even spacing
        let start = floor(paddedMin / bestStep) * bestStep
        let end = start + (bestStep * Double(desiredLabelCount - 1))
        
        // Generate ticks with even spacing
        var ticks: [Double] = []
        var currentTick = start
        
        while currentTick <= end {
            ticks.append(currentTick)
            currentTick += bestStep
        }
        
        // Ensure we have at least 3 ticks for better curve visibility
        if ticks.count < 3 {
            while ticks.count < 3 {
                if let lastTick = ticks.last {
                    ticks.append(lastTick + bestStep)
                } else {
                    ticks.append(start)
                }
            }
        }
        
        // For very small ranges, add extra ticks to ensure curve visibility (whole numbers only)
        if totalRange < 10 && ticks.count < 5 {
            let extraStep = max(1.0, bestStep / 2)
            var extraTicks: [Double] = []
            var extraTick = start
            
            while extraTick <= end {
                if !ticks.contains(where: { abs($0 - extraTick) < bestStep * 0.1 }) {
                    extraTicks.append(extraTick)
                }
                extraTick += extraStep
            }
            
            // Add extra ticks while maintaining reasonable count
            ticks.append(contentsOf: extraTicks.prefix(5 - ticks.count))
            ticks.sort()
        }
        
        // Ensure goal weight is included if it's within the domain
        if goalWeight >= start && goalWeight <= end && !ticks.contains(where: { abs($0 - goalWeight) < bestStep * 0.1 }) {
            // Find closest tick and replace it with goal weight
            if let closestIndex = ticks.enumerated().min(by: { abs($0.element - goalWeight) < abs($1.element - goalWeight) })?.offset {
                ticks[closestIndex] = goalWeight
            }
        }
        return (ticks.first!, ticks.last!, bestStep, ticks, ticks.first!...ticks.last!)
    }
} 
