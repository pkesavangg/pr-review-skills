import Foundation

/// Implementation of DashboardFormatterProtocol for formatting dashboard display values.
/// Handles formatting of dates, weights, metrics, and other dashboard display values.
@MainActor
final class DashboardFormatter: DashboardFormatterProtocol {
    
    // MARK: - Constants
    
    private static let allowedNumericCharacters = CharacterSet(charactersIn: "0123456789.-")
    
    // Cached NumberFormatter for Y-axis tick labels to avoid recreating on every call
    private static let yAxisNumberFormatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        return formatter
    }()
    
    // Cached ISO8601DateFormatter instances for parsing entry dates
    private static let iso8601FormatterWithFractionalSeconds: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()
    
    private static let iso8601FormatterWithoutFractionalSeconds: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()
    
    // MARK: - Weight Formatting
    
    func formatYAxisTickLabel(_ weight: Double) -> String {
        let value = roundedGoalWeight(weight)
        // Thousand separators; keep decimals only when value has fractional part
        let formatter = Self.yAxisNumberFormatter
        let hasFractionalPart = value != floor(value)
        formatter.maximumFractionDigits = hasFractionalPart ? 2 : 0
        formatter.minimumFractionDigits = hasFractionalPart ? 2 : 0
        return formatter.string(from: NSNumber(value: value)) ?? String(format: "%.0f", value)
    }
    
    func roundedGoalWeight(_ weight: Double) -> Double {
        return weight.rounded(.toNearestOrAwayFromZero)
    }
    
    // MARK: - Date Formatting
    
    func formatChartDate(_ date: Date, period: TimePeriod) -> String {
        // Use cached formatter from DateTimeTools instead of creating new DateFormatter each call
        switch period {
        case .week, .month:
            return DateTimeTools.formatter("MMM d").string(from: date)
        case .year, .total:
            return DateTimeTools.formatter("MMM yyyy").string(from: date)
        }
    }
    
    func formatMetricInfoSingleDate(_ date: Date, period: TimePeriod) -> String {
        // Use cached formatter from DateTimeTools instead of creating new DateFormatter each call
        switch period {
        case .week, .month:
            return DateTimeTools.formatter("MMM d, yyyy").string(from: date)
        case .year, .total:
            return DateTimeTools.formatter("MMM yyyy").string(from: date)
        }
    }
    
    // swiftlint:disable:next function_parameter_count
    func formatMetricInfoDateLabel(
        entryDate: Date?,
        isFromHistory: Bool,
        period: TimePeriod,
        selectedPointDate: Date?,
        crosshairDate: Date?,
        weightLabel: String
    ) -> String {
        if let entryDate = entryDate {
            let prefix = isFromHistory ? "Measurement taken" : "day average"
            // Use cached formatter from DateTimeTools instead of creating new DateFormatter each call
            let format = isFromHistory ? "MMMM d, yyyy" : "MMM d, yyyy"
            let dateText = DateTimeTools.formatter(format).string(from: entryDate)
            return isFromHistory ? "\(prefix) \(dateText)" : composeMetricInfoLabel(prefix: prefix, dateText: dateText)
        }
        
        if let selectedPointDate = selectedPointDate {
            let prefix = selectionPrefix(for: period)
            let dateText = formatMetricInfoSingleDate(selectedPointDate, period: period)
            return composeMetricInfoLabel(prefix: prefix, dateText: dateText)
        }
        
        if let crosshairDate = crosshairDate {
            let prefix = selectionPrefix(for: period)
            let dateText = formatMetricInfoSingleDate(crosshairDate, period: period)
            return composeMetricInfoLabel(prefix: prefix, dateText: dateText)
        }
        
        let prefix = "\(period.rawValue) average"
        let dateText = weightLabel // already computed from visible region
        return composeMetricInfoLabel(prefix: prefix, dateText: dateText)
    }
    
    func parseEntryDate(from entryDTO: BathScaleOperationDTO) -> Date? {
        if let date = entryDTO.date {
            return date
        }
        
        guard let timestamp = entryDTO.entryTimestamp else {
            return nil
        }
        
        // Try with fractional seconds first
        if let date = Self.iso8601FormatterWithFractionalSeconds.date(from: timestamp) {
            return date
        }
        
        // Fallback to without fractional seconds
        return Self.iso8601FormatterWithoutFractionalSeconds.date(from: timestamp)
    }
    
    func isDashboardEntry(_ entryDTO: BathScaleOperationDTO) -> Bool {
        return entryDTO.source == "dashboard"
    }
    
    // MARK: - Metric Value Formatting
    
    func formattedMetricValue(for metric: (preLabel: String?, value: String)) -> String {
        let raw = metric.value.trimmingCharacters(in: .whitespacesAndNewlines)
        // Extract numeric portion to check for zero (handles "0", "0.0", etc.)
        let numericScalars = raw.unicodeScalars.filter { Self.allowedNumericCharacters.contains($0) }
        let numericChars = String(String.UnicodeScalarView(numericScalars))
        // Check if value is placeholder or zero
        let isPlaceholder = raw == DashboardStrings.placeholder || (numericChars.isEmpty == false && Double(numericChars) == 0)
        
        if isPlaceholder {
            // If there's a preLabel (e.g., "Lv." for visceral fat), show "Lv. --" instead of just "--"
            if let preLabel = metric.preLabel {
                return "\(preLabel) \(DashboardStrings.placeholder)"
            }
            return DashboardStrings.placeholder
        }
        return metric.preLabel.map { "\($0) \(metric.value)" } ?? metric.value
    }
    
    // MARK: - Helper Methods
    
    func composeMetricInfoLabel(prefix: String, dateText: String) -> String {
        return "\(prefix) \(dateText)".lowercased()
    }
    
    func selectionPrefix(for period: TimePeriod) -> String {
        switch period {
        case .week, .month: return "day average"
        case .year, .total: return "month average"
        }
    }
}
