import Foundation
import SwiftUI

// MARK: - YAxisScale Model

struct YAxisScale {
    let min: Int
    let max: Int
    let step: Int
    let labels: [Int]
    let domain: ClosedRange<Double>
    let average: Double
}

struct YAxisCalculator {

    static func calculateYAxis(
        weights: [Double],
        goalWeight: Double,
        chartHeight: CGFloat = 265, // Default chart height
        minTickSpacing: CGFloat = 40 // Minimum spacing between ticks in points
    ) -> YAxisScale {
        
        // Calculate optimal number of ticks based on chart height
        let optimalTickCount = max(3, min(8, Int(chartHeight / minTickSpacing)))
        
        guard !weights.isEmpty else {
            // No data — show goal weight as center
            let goal = Int(goalWeight.rounded())
            let labels = [goal - 1, goal, goal + 1, goal + 2]
            return YAxisScale(
                min: labels.first!,
                max: labels.last!,
                step: 1,
                labels: labels,
                domain: Double(labels.first!)...Double(labels.last!),
                average: goalWeight
            )
        }

        let average = calculateAverage(weights: weights)

        let minValue = weights.min() ?? goalWeight
        let maxValue = weights.max() ?? goalWeight

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
            labels: scale.labels,
            domain: Double(scale.min)...Double(scale.max),
            average: average
        )
    }

    static func calculateAverage(weights: [Double]) -> Double {
        guard !weights.isEmpty else { return 0 }
        if weights.count == 1 {
            return weights[0] // fallback to same value
        }
        return weights.reduce(0, +) / Double(weights.count)
    }
}

fileprivate struct YAxisHelper {
    static func generateYAxis(
        minValue: Double,
        maxValue: Double,
        goalWeight: Double,
        desiredLabelCount: Int = 4
    ) -> (min: Int, max: Int, step: Int, labels: [Int]) {
        
        // Include goal weight in the range calculation
        let allValues = [minValue, maxValue, goalWeight]
        let dataMin = allValues.min() ?? minValue
        let dataMax = allValues.max() ?? maxValue
        
        // Add smaller padding for more precise ranges
        let range = dataMax - dataMin
        let padding = range * 0.1 // 10% padding for better visibility
        
        let paddedMin = dataMin - padding
        let paddedMax = dataMax + padding
        
        // Calculate appropriate step size based on the range and desired label count
        let totalRange = paddedMax - paddedMin
        let idealStep = totalRange / Double(desiredLabelCount - 1)
        
        // Find the best step size from common increments
        let stepSizes = [1, 2, 5, 10, 20, 25, 50, 100]
        var bestStep = 1
        var bestFit = Double.infinity
        
        for step in stepSizes {
            let stepDouble = Double(step)
            let fit = abs(stepDouble - idealStep)
            if fit < bestFit {
                bestFit = fit
                bestStep = step
            }
        }
        
        // Calculate start and end points to ensure even spacing
        let start = Int(floor(paddedMin / Double(bestStep))) * bestStep
        let end = start + (bestStep * (desiredLabelCount - 1))
        
        // Generate labels with even spacing
        let labels = Array(stride(from: start, through: end, by: bestStep))
        
        // Ensure goal weight is always included if it's within the range
        var finalLabels = labels
        let goalInt = Int(round(goalWeight))
        
        if !finalLabels.contains(goalInt) {
            // Check if goal weight is far below the domain
            if goalInt < finalLabels.first! {
                // Prepend goal weight and adjust spacing
                finalLabels.insert(goalInt, at: 0)
                // Recalculate spacing to maintain even distribution
                let newRange = finalLabels.last! - finalLabels.first!
                let newStep = newRange / (finalLabels.count - 1)
                finalLabels = Array(stride(from: finalLabels.first!, through: finalLabels.last!, by: Int(newStep)))
                print("Hello: YAxisHelper - Goal weight \(goalInt) prepended to labels")
            }
            // Check if goal weight is far above the domain
            else if goalInt > finalLabels.last! {
                // Append goal weight and adjust spacing
                finalLabels.append(goalInt)
                // Recalculate spacing to maintain even distribution
                let newRange = finalLabels.last! - finalLabels.first!
                let newStep = newRange / (finalLabels.count - 1)
                finalLabels = Array(stride(from: finalLabels.first!, through: finalLabels.last!, by: Int(newStep)))
                print("Hello: YAxisHelper - Goal weight \(goalInt) appended to labels")
            }
            // Goal weight is inside domain but not on a step boundary
            else {
                // Find the closest label to goal weight and replace it
                let closestIndex = finalLabels.enumerated().min { abs($0.element - goalInt) < abs($1.element - goalInt) }?.offset ?? 0
                finalLabels[closestIndex] = goalInt
                finalLabels.sort()
                print("Hello: YAxisHelper - Goal weight \(goalInt) inserted at closest position")
            }
        }
        
        print("Hello: YAxisHelper - Input: minValue=\(minValue), maxValue=\(maxValue), goalWeight=\(goalWeight), desiredLabelCount=\(desiredLabelCount)")
        print("Hello: YAxisHelper - Calculated: dataMin=\(dataMin), dataMax=\(dataMax), range=\(range), padding=\(padding)")
        print("Hello: YAxisHelper - Final: start=\(finalLabels.first!), end=\(finalLabels.last!), labels=\(finalLabels)")
        
        return (finalLabels.first!, finalLabels.last!, bestStep, finalLabels)
    }
} 