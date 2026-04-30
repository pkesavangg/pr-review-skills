import Foundation

/// Comprehensive error handling for Dashboard operations
enum DashboardError: Error, LocalizedError {

    // MARK: - Data Loading Errors
    case dataLoadingFailed(Error)
    case noActiveAccount
    case noEntriesFound
    case cacheUpdateFailed(String)

    // MARK: - API Errors
    case apiSyncFailed(Error)
    case configurationLoadFailed(Error)
    case metricsSaveFailed(Error)

    // MARK: - Metric Errors
    case invalidMetricData(String)
    case metricConversionFailed(String)
    case unsupportedMetricType(String)

    // MARK: - Scale Detection Errors
    case scaleDetectionFailed(Error)
    case unsupportedScaleType(String)

    // MARK: - Graph Errors
    case chartDataGenerationFailed(String)
    case invalidTimeRange(String)
    case scrollPositionUpdateFailed(String)

    // MARK: - Goal Errors
    case goalCalculationFailed(String)
    case weightlessSettingsInvalid(String)
    case unitConversionFailed(String)

    // MARK: - LocalizedError Implementation
    var errorDescription: String? {
        switch self {
        // Data Loading Errors
        case .dataLoadingFailed(let error):
            return "Failed to load dashboard data: \(error.localizedDescription)"
        case .noActiveAccount:
            return "No active account found. Please log in to view dashboard."
        case .noEntriesFound:
            return "No entries found for the selected time period."
        case .cacheUpdateFailed(let key):
            return "Failed to update cache for key: \(key)"

        // API Errors
        case .apiSyncFailed(let error):
            return "Failed to sync with server: \(error.localizedDescription)"
        case .configurationLoadFailed(let error):
            return "Failed to load dashboard configuration: \(error.localizedDescription)"
        case .metricsSaveFailed(let error):
            return "Failed to save dashboard metrics: \(error.localizedDescription)"

        // Metric Errors
        case .invalidMetricData(let metric):
            return "Invalid data for metric: \(metric)"
        case .metricConversionFailed(let metric):
            return "Failed to convert metric: \(metric)"
        case .unsupportedMetricType(let type):
            return "Unsupported metric type: \(type)"

        // Scale Detection Errors
        case .scaleDetectionFailed(let error):
            return "Failed to detect scale type: \(error.localizedDescription)"
        case .unsupportedScaleType(let type):
            return "Unsupported scale type: \(type)"

        // Graph Errors
        case .chartDataGenerationFailed(let reason):
            return "Failed to generate chart data: \(reason)"
        case .invalidTimeRange(let range):
            return "Invalid time range: \(range)"
        case .scrollPositionUpdateFailed(let reason):
            return "Failed to update scroll position: \(reason)"

        // Goal Errors
        case .goalCalculationFailed(let reason):
            return "Failed to calculate goal progress: \(reason)"
        case .weightlessSettingsInvalid(let reason):
            return "Invalid weightless settings: \(reason)"
        case .unitConversionFailed(let reason):
            return "Failed to convert units: \(reason)"
        }
    }

    var recoverySuggestion: String? {
        switch self {
        case .noActiveAccount:
            return "Please log in to your account and try again."
        case .noEntriesFound:
            return "Try selecting a different time period or add some entries."
        case .apiSyncFailed:
            return "Check your internet connection and try again."
        case .configurationLoadFailed:
            return "Try refreshing the dashboard or restart the app."
        case .scaleDetectionFailed:
            return "Ensure your scale is properly connected and try again."
        case .invalidMetricData:
            return "The metric data may be corrupted. Try refreshing the dashboard."
        case .goalCalculationFailed:
            return "Check your goal settings and try again."
        default:
            return "Please try again or contact support if the problem persists."
        }
    }

    var failureReason: String? {
        switch self {
        case .dataLoadingFailed:
            return "The dashboard data could not be loaded from the database."
        case .apiSyncFailed:
            return "The server request failed or timed out."
        case .invalidMetricData:
            return "The metric data format is incorrect or missing required fields."
        case .scaleDetectionFailed:
            return "The scale type could not be determined from the device information."
        case .chartDataGenerationFailed:
            return "The chart data could not be generated from the available entries."
        case .goalCalculationFailed:
            return "The goal progress calculation failed due to invalid input data."
        default:
            return nil
        }
    }
}

// MARK: - Error Logging Extension
extension DashboardError {
    /// Log the error with appropriate severity
  @MainActor func log(with logger: LoggerService, tag: String = "DashboardError") {
        let severity: LogLevel = {
            switch self {
            case .noActiveAccount, .noEntriesFound:
                return .info
            case .cacheUpdateFailed, .invalidMetricData, .metricConversionFailed:
                return .error
            case .dataLoadingFailed, .apiSyncFailed, .configurationLoadFailed, .metricsSaveFailed:
                return .error
            case .scaleDetectionFailed, .chartDataGenerationFailed, .goalCalculationFailed:
                return .error
            default:
                return .error
            }
        }()

        logger.log(level: severity, tag: tag, message: self.errorDescription ?? "Unknown dashboard error")
    }
}

// MARK: - Result Type Extension
extension Result where Failure == DashboardError {
    /// Log error if result is failure
  @MainActor func logError(with logger: LoggerService, tag: String = "DashboardResult") {
        if case .failure(let error) = self {
            error.log(with: logger, tag: tag)
        }
    }
}
