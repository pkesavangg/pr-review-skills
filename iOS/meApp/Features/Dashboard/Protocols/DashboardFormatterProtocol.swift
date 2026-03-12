import Foundation

/// Protocol defining the interface for dashboard formatting operations.
/// Handles formatting of dates, weights, metrics, and other dashboard display values.
@MainActor
protocol DashboardFormatterProtocol {
    
    // MARK: - Weight Formatting
    
    /// Formats a weight value for Y-axis tick labels with thousand separators.
    /// - Parameter weight: The weight value to format.
    /// - Returns: Formatted weight string with appropriate decimal places.
    func formatYAxisTickLabel(_ weight: Double) -> String
    
    /// Rounds a weight value to the nearest integer.
    /// - Parameter weight: The weight value to round.
    /// - Returns: Rounded weight value.
    func roundedGoalWeight(_ weight: Double) -> Double
    
    // MARK: - Date Formatting
    
    /// Formats a date for chart display based on the selected time period.
    /// - Parameters:
    ///   - date: The date to format.
    ///   - period: The selected time period (week/month/year/total).
    /// - Returns: Formatted date string.
    func formatChartDate(_ date: Date, period: TimePeriod) -> String
    
    /// Formats a single date for metric info display based on time period.
    /// - Parameters:
    ///   - date: The date to format.
    ///   - period: The selected time period.
    /// - Returns: Formatted date string.
    func formatMetricInfoSingleDate(_ date: Date, period: TimePeriod) -> String
    
    /// Formats a date label for metric info sheet.
    /// - Parameters:
    ///   - entryDate: Optional entry date.
    ///   - isFromHistory: Whether the entry is from history.
    ///   - period: The selected time period.
    ///   - selectedPointDate: Optional selected point date.
    ///   - crosshairDate: Optional crosshair date.
    ///   - weightLabel: The weight label to use as fallback.
    /// - Returns: Formatted date label string.
    func formatMetricInfoDateLabel( // swiftlint:disable:this function_parameter_count
        entryDate: Date?,
        isFromHistory: Bool,
        period: TimePeriod,
        selectedPointDate: Date?,
        crosshairDate: Date?,
        weightLabel: String
    ) -> String
    
    /// Parses a date from an entry DTO, handling multiple timestamp formats.
    /// - Parameter entryDTO: The entry DTO to parse.
    /// - Returns: Parsed date, or nil if parsing fails.
    func parseEntryDate(from entryDTO: BathScaleOperationDTO) -> Date?
    
    /// Checks if an entry DTO is from the dashboard.
    /// - Parameter entryDTO: The entry DTO to check.
    /// - Returns: True if the entry is from dashboard, false otherwise.
    func isDashboardEntry(_ entryDTO: BathScaleOperationDTO) -> Bool
    
    // MARK: - Metric Value Formatting
    
    /// Formats a metric value for display, handling placeholders and pre-labels.
    /// - Parameter metric: A tuple containing optional pre-label and value string.
    /// - Returns: Formatted metric value string.
    func formattedMetricValue(for metric: (preLabel: String?, value: String)) -> String
    
    // MARK: - Helper Methods
    
    /// Composes a metric info label from prefix and date text.
    /// - Parameters:
    ///   - prefix: The prefix string (e.g., "day average").
    ///   - dateText: The date text to append.
    /// - Returns: Composed label string in lowercase.
    func composeMetricInfoLabel(prefix: String, dateText: String) -> String
    
    /// Gets the selection prefix for a given time period.
    /// - Parameter period: The time period.
    /// - Returns: The prefix string (e.g., "day average" or "month average").
    func selectionPrefix(for period: TimePeriod) -> String
}
