import Foundation

/// Protocol defining metrics calculation operations for dashboard
@MainActor
protocol DashboardMetricsCalculatorProtocol {
    /// Returns the average weight for the given operations with proper rounding
    /// - Parameters:
    ///   - operations: The operations to calculate average from
    ///   - isWeightlessMode: Whether weightless mode is enabled
    ///   - anchorWeight: The anchor weight for weightless mode
    ///   - convertWeight: Function to convert stored weight to display weight
    /// - Returns: The average weight rounded to 1 decimal place, or 0 if no operations are available
    func getCurrentAverageWeight(
        from operations: [BathScaleWeightSummary],
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: @escaping (Int) -> Double
    ) -> Double

    /// Calculates the display weight based on selection and visible operations
    /// - Parameters:
    ///   - context: The calculation context containing all necessary data
    /// - Returns: The display weight, or nil if not available
    func calculateDisplayWeight(context: DisplayWeightContext) -> Double?

    /// Creates an entry for metric info display based on current dashboard context
    /// - Parameters:
    ///   - context: The entry creation context containing all necessary data
    /// - Returns: An Entry object representing the current dashboard context
    func createEntryForMetricInfo(context: EntryCreationContext) -> Entry
}

/// Context for calculating display weight
struct DisplayWeightContext {
    let selectedPoint: BathScaleWeightSummary?
    let selectedDate: Date?
    let operations: [BathScaleWeightSummary]
    let visibleOperations: [BathScaleWeightSummary]
    let operationsForLabel: [BathScaleWeightSummary]
    let isWeightlessMode: Bool
    let anchorWeight: Double?
    let period: TimePeriod
    let convertWeight: (Int) -> Double
    let interpolatedWeight: (Date, [BathScaleWeightSummary], Bool, Double?, @escaping (Int) -> Double) -> Double?
    let interpolatedAverage: ([BathScaleWeightSummary], TimePeriod, Bool, Double?, @escaping (Int) -> Double, DateInterval?) -> Double?
    let weightlessDisplay: ([BathScaleWeightSummary], Double?, TimePeriod, @escaping (Int) -> Double) -> Double?
    let labelRangeForPeriod: (TimePeriod) -> DateInterval?
}

/// Context for creating entry for metric info
struct EntryCreationContext {
    let selectedPoint: BathScaleWeightSummary?
    let selectedDate: Date?
    let operations: [BathScaleWeightSummary]
    let visibleOperations: [BathScaleWeightSummary]
    let metrics: [MetricItem]
    let isWeightlessMode: Bool
    let anchorWeight: Double?
    let period: TimePeriod
    let weightUnit: WeightUnit
    let latestWeightStored: Int
    let convertWeight: (Int) -> Double
    let interpolatedWeight: (Date, [BathScaleWeightSummary], Bool, Double?, @escaping (Int) -> Double) -> Double?
    let interpolatedAverage: ([BathScaleWeightSummary], TimePeriod, Bool, Double?, @escaping (Int) -> Double, DateInterval?) -> Double?
}
