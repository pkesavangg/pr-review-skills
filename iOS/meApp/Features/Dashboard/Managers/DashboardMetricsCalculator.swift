import Foundation

/// Service for calculating dashboard metrics and creating entries
@MainActor
class DashboardMetricsCalculator: DashboardMetricsCalculatorProtocol {

    // MARK: - Initialization
    init() {}

    // MARK: - Average Weight Calculation

    /// Returns the average weight for the given operations with proper rounding
    func getCurrentAverageWeight(
        from operations: [BathScaleWeightSummary],
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: @escaping (Int) -> Double
    ) -> Double {
        // Return 0 if no operations are available
        guard !operations.isEmpty else {
            return 0
        }

        // Calculate weight values with proper error handling
        let weightValues = operations.compactMap { summary -> Double? in
            if isWeightlessMode {
                guard let anchorWeight = anchorWeight else {
                    return nil
                }
                let currentWeight = convertWeight(Int(summary.weight))
                return currentWeight - anchorWeight
            } else {
                return convertWeight(Int(summary.weight))
            }
        }

        // Return 0 if no valid weight values were calculated
        guard !weightValues.isEmpty else {
            return 0
        }

        // Calculate average with proper rounding to 1 decimal place
        let sum = weightValues.reduce(0, +)
        let average = sum / Double(weightValues.count)

        // Round to 1 decimal place using a more robust approach to handle floating-point precision
        let roundedAverage = (average * 10).rounded(.toNearestOrAwayFromZero) / 10
        return roundedAverage
    }

    // MARK: - Display Weight Calculation

    /// Calculates the display weight based on selection and visible operations
    func calculateDisplayWeight(context: DisplayWeightContext) -> Double? {
        // If a concrete point is selected, ALWAYS show its exact weight value
        if let selectedPoint = context.selectedPoint {
            if context.isWeightlessMode {
                guard let anchorWeight = context.anchorWeight else { return nil }
                let currentWeight = context.convertWeight(Int(selectedPoint.weight))
                return currentWeight - anchorWeight
            } else {
                return context.convertWeight(Int(selectedPoint.weight))
            }
        }

        // Else, if a crosshair date is selected (can be on empty day), compute interpolated weight at that date
        if let selectedDate = context.selectedDate {
            return context.interpolatedWeight(
                selectedDate,
                context.operations,
                context.isWeightlessMode,
                context.anchorWeight,
                context.convertWeight
            )
        }

        // When no selection, show average of visible region if available
        // Use operations filtered to match the date range shown in the label
        let opsToUse = context.operationsForLabel

        // If no visible operations, but we have data and we're not in total view,
        // calculate interpolated average for the visible range
        if opsToUse.isEmpty && !context.operations.isEmpty && context.period != .total {
            let labelRange = context.labelRangeForPeriod(context.period)
            let interpolatedAverage = context.interpolatedAverage(
                context.operations,
                context.period,
                context.isWeightlessMode,
                context.anchorWeight,
                context.convertWeight,
                labelRange
            )
            return interpolatedAverage
        }

        // Check if weightless mode is enabled
        if context.isWeightlessMode {
            return context.weightlessDisplay(
                opsToUse,
                context.anchorWeight,
                context.period,
                context.convertWeight
            )
        }

        // Calculate average of operations in visible region (or all if no visible region)
        let weights = opsToUse.map { context.convertWeight(Int($0.weight)) }
        guard !weights.isEmpty else { return nil }
        let averageWeight = weights.reduce(0, +) / Double(weights.count)

        // Round to 1 decimal place using a more robust approach to handle floating-point precision
        let roundedAverage = (averageWeight * 10).rounded(.toNearestOrAwayFromZero) / 10
        return roundedAverage
    }

    // MARK: - Entry Creation

    /// Creates an entry for metric info display based on current dashboard context
    func createEntryForMetricInfo(context: EntryCreationContext) -> Entry {
        // Build an entry that mirrors the current dashboard context
        // If a chart point is selected, use that point's values
        // Otherwise, use averages of the currently visible operations
        // Initialize with a timestamp that we'll override below based on selection/context
        let entry = Entry(
            id: UUID(),
            entryTimestamp: DateTimeTools.getCurrentDatetimeIsoString(),
            accountId: "dashboard",
            operationType: OperationType.create.rawValue,
            deviceType: "scale",
            isSynced: true
        )

        if let point = context.selectedPoint {
            return createEntryFromSelectedPoint(entry: entry, point: point, metrics: context.metrics)
        }

        // Interpolated selection: show interpolated weight for the selectedXValue and placeholders for body metrics
        if let selectedDate = context.selectedDate {
            return createEntryFromInterpolatedDate(
                entry: entry,
                selectedDate: selectedDate,
                context: context
            )
        }

        // No selection: compute visible-window averages to mirror tiles and weight label
        let ops = context.visibleOperations
        if ops.isEmpty {
            return createEntryFromInterpolatedAverage(
                entry: entry,
                operations: context.operations,
                period: context.period,
                isWeightlessMode: context.isWeightlessMode,
                anchorWeight: context.anchorWeight,
                weightUnit: context.weightUnit,
                convertWeight: context.convertWeight,
                interpolatedAverage: context.interpolatedAverage
            )
        }

        return createEntryFromVisibleOperations(
            entry: entry,
            operations: ops,
            weightUnit: context.weightUnit,
            latestWeightStored: context.latestWeightStored,
            convertWeight: context.convertWeight
        )
    }

    // MARK: - Private Helper Methods

    /// Creates entry from a selected point on the chart
    private func createEntryFromSelectedPoint(entry: Entry, point: BathScaleWeightSummary, metrics: [MetricItem]) -> Entry {
        // Helpers to convert Double? to Int? with 0 -> nil
        func intOrNil(_ x: Double?) -> Int? {
            guard let x = x else { return nil }
            let intValue = Int(x.rounded())
            return intValue == 0 ? nil : intValue
        }

        func scaled10OrNil(_ x: Double?, metricLabel: String) -> Int? {
            guard let x = x else { return nil }

            if let metricItem = metrics.first(where: { $0.label == metricLabel }) {
                let tileValue = metricItem.value.trimmingCharacters(in: .whitespacesAndNewlines)
                if tileValue == DashboardStrings.placeholder || tileValue == "0" || tileValue == "0.0" {
                    return nil
                }
            }

            // For BMR and visceral fat: x is in stored format (scaled by 10) from BathScaleWeightSummary
            // Divide by 10 to get display format before formatting to properly detect zero values
            // This aligns with how MetricDetailView displays these values
            let displayValue = x / 10.0
            let formatted = BodyMetricsConvertor.convert(displayValue, shouldCompose: false, wholeNumber: true)

            if formatted == "0" || formatted == "0.0" || formatted == "--" {
                return nil
            }
            // x is already in stored format (scaled by 10), so convert to Int directly
            let intValue = Int(x.rounded())
            return intValue == 0 ? nil : intValue
        }

        let storedWeight: Int? = {
            let weightValue = Int(point.weight.rounded())
            return weightValue == 0 ? nil : weightValue
        }()

        // Use the actual point's timestamp
        entry.entryTimestamp = DateTimeTools.isoFormatter().string(from: point.date)

        entry.scaleEntry = BathScaleEntry(
            weight: storedWeight,
            bodyFat: intOrNil(point.bodyFat),
            muscleMass: intOrNil(point.muscleMass),
            water: intOrNil(point.water),
            bmi: intOrNil(point.bmi),
            source: "dashboard"
        )
        entry.scaleEntryMetric = BathScaleMetric(
            bmr: scaled10OrNil(point.bmr, metricLabel: DashboardStrings.bmrKcal),
            metabolicAge: intOrNil(point.metabolicAge),
            proteinPercent: intOrNil(point.proteinPercent),
            pulse: intOrNil(point.pulse),
            skeletalMusclePercent: intOrNil(point.skeletalMusclePercent),
            subcutaneousFatPercent: intOrNil(point.subcutaneousFatPercent),
            visceralFatLevel: scaled10OrNil(point.visceralFatLevel, metricLabel: DashboardStrings.visceralFat),
            boneMass: intOrNil(point.boneMass),
            impedance: nil,
            unit: nil
        )
        return entry
    }

    /// Creates entry from an interpolated date selection
    private func createEntryFromInterpolatedDate(
        entry: Entry,
        selectedDate: Date,
        context: EntryCreationContext
    ) -> Entry {
        // Compute interpolated display weight in current UI context
        let interpolated = context.interpolatedWeight(
            selectedDate,
            context.operations,
            context.isWeightlessMode,
            context.anchorWeight,
            context.convertWeight
        )
        // Map display weight to stored (handle weightless by adding anchor back)
        let displayAbsolute: Double? = {
            if let interpolatedWeight = interpolated {
                if context.isWeightlessMode, let anchor = context.anchorWeight {
                    return interpolatedWeight + anchor
                } else {
                    return interpolatedWeight
                }
            }
            return nil
        }()
        let storedWeight: Int? = {
            guard let displayAbs = displayAbsolute else { return nil }
            return ConversionTools.convertDisplayToStored(displayAbs, isMetric: context.weightUnit == .kg)
        }()

        // Timestamp is the crosshair date selected by the user
        entry.entryTimestamp = DateTimeTools.isoFormatter().string(from: selectedDate)

        entry.scaleEntry = BathScaleEntry(
            weight: storedWeight,
            bodyFat: nil,
            muscleMass: nil,
            water: nil,
            bmi: nil,
            source: "dashboard"
        )
        entry.scaleEntryMetric = BathScaleMetric(
            bmr: nil,
            metabolicAge: nil,
            proteinPercent: nil,
            pulse: nil,
            skeletalMusclePercent: nil,
            subcutaneousFatPercent: nil,
            visceralFatLevel: nil,
            boneMass: nil,
            impedance: nil,
            unit: nil
        )
        return entry
    }

    /// Creates entry from interpolated average when no visible operations
    private func createEntryFromInterpolatedAverage( // swiftlint:disable:this function_parameter_count
        entry: Entry,
        operations: [BathScaleWeightSummary],
        period: TimePeriod,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        weightUnit: WeightUnit,
        convertWeight: @escaping (Int) -> Double,
        interpolatedAverage: ([BathScaleWeightSummary], TimePeriod, Bool, Double?, @escaping (Int) -> Double, DateInterval?) -> Double?
    ) -> Entry {
        var storedWeightForInfo: Int?

        if !operations.isEmpty {
            let interpolatedAvg = interpolatedAverage(
                operations,
                period,
                isWeightlessMode,
                anchorWeight,
                convertWeight,
                nil
            )
            if let displayAvg = interpolatedAvg {
                storedWeightForInfo = ConversionTools.convertDisplayToStored(displayAvg, isMetric: weightUnit == .kg)
            }
        }

        entry.scaleEntry = BathScaleEntry(
            weight: storedWeightForInfo,
            bodyFat: nil,
            muscleMass: nil,
            water: nil,
            bmi: nil,
            source: "dashboard"
        )
        entry.scaleEntryMetric = BathScaleMetric(
            bmr: nil,
            metabolicAge: nil,
            proteinPercent: nil,
            pulse: nil,
            skeletalMusclePercent: nil,
            subcutaneousFatPercent: nil,
            visceralFatLevel: nil,
            boneMass: nil,
            impedance: nil,
            unit: nil
        )
        return entry
    }

    /// Creates entry from visible operations averages
    private func createEntryFromVisibleOperations( // swiftlint:disable:this function_body_length
        entry: Entry,
        operations: [BathScaleWeightSummary],
        weightUnit: WeightUnit,
        latestWeightStored: Int,
        convertWeight: @escaping (Int) -> Double
    ) -> Entry {
        // Average helpers
        func avg(_ values: [Double?]) -> Double? {
            let xs = values.compactMap { $0 }
            guard !xs.isEmpty else { return nil }
            return xs.reduce(0, +) / Double(xs.count)
        }

        // Helpers for averages: Double? -> Int? with 0 -> nil
        func intOrNil(_ x: Double?) -> Int? {
            guard let x = x else { return nil }
            let intValue = Int(x.rounded())
            return intValue == 0 ? nil : intValue
        }
        func scaled10OrNil(_ x: Double?) -> Int? {
            guard let x = x else { return nil }
            let intValue = Int((x * 10.0).rounded())
            return intValue == 0 ? nil : intValue
        }

        // Weight average in stored units
        let avgStoredWeightOpt: Int? = {
            // Convert each weight to display format and calculate average
            let weightValues = operations.map { convertWeight(Int($0.weight)) }
            guard !weightValues.isEmpty else {
                return latestWeightStored == 0 ? nil : latestWeightStored
            }
            let sum = weightValues.reduce(0, +)
            let average = sum / Double(weightValues.count)

            let roundedAverage = (average * 100).rounded(.toNearestOrAwayFromZero) / 100

            // Convert back to stored format
            let stored = ConversionTools.convertDisplayToStored(roundedAverage, isMetric: weightUnit == .kg)
            return stored == 0 ? nil : stored
        }()

        // Build scaleEntry from averages (convert display doubles to stored Ints where appropriate)
        let avgBodyFat = avg(operations.map { $0.bodyFat }).map { Int($0.rounded()) }
        let avgMuscle = avg(operations.map { $0.muscleMass }).map { Int($0.rounded()) }
        let avgWater = avg(operations.map { $0.water }).map { Int($0.rounded()) }
        let avgBmi = avg(operations.map { $0.bmi }).map { Int($0.rounded()) }
        entry.scaleEntry = BathScaleEntry(
            weight: avgStoredWeightOpt,
            bodyFat: intOrNil(avgBodyFat.map { Double($0) }),
            muscleMass: intOrNil(avgMuscle.map { Double($0) }),
            water: intOrNil(avgWater.map { Double($0) }),
            bmi: intOrNil(avgBmi.map { Double($0) }),
            source: "dashboard"
        )

        // Metric entry: visceralFat and bmr are stored scaled by 10
        let avgBmr = intOrNil(avg(operations.map { $0.bmr }))
        let avgMetAge = intOrNil(avg(operations.map { $0.metabolicAge }))
        let avgProtein = intOrNil(avg(operations.map { $0.proteinPercent }))
        let avgPulse = intOrNil(avg(operations.map { $0.pulse }))
        let avgSkel = intOrNil(avg(operations.map { $0.skeletalMusclePercent }))
        let avgSubFat = intOrNil(avg(operations.map { $0.subcutaneousFatPercent }))
        let avgVisceral = intOrNil(avg(operations.map { $0.visceralFatLevel }))
        let avgBone = intOrNil(avg(operations.map { $0.boneMass }))

        entry.scaleEntryMetric = BathScaleMetric(
            bmr: avgBmr,
            metabolicAge: avgMetAge,
            proteinPercent: avgProtein,
            pulse: avgPulse,
            skeletalMusclePercent: avgSkel,
            subcutaneousFatPercent: avgSubFat,
            visceralFatLevel: avgVisceral,
            boneMass: avgBone,
            impedance: nil,
            unit: nil
        )
        return entry
    }
}
