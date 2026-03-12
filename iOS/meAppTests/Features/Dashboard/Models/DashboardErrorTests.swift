import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct DashboardErrorTests {

    private func wrappedError(_ message: String) -> NSError {
        NSError(
            domain: "DashboardErrorTests",
            code: 1,
            userInfo: [NSLocalizedDescriptionKey: message]
        )
    }

    @Test("errorDescription: covers data and API-backed cases")
    func errorDescriptionDataAndAPI() {
        let cases: [(DashboardError, String)] = [
            (.dataLoadingFailed(wrappedError("db down")), "Failed to load dashboard data: db down"),
            (.noActiveAccount, "No active account found. Please log in to view dashboard."),
            (.noEntriesFound, "No entries found for the selected time period."),
            (.cacheUpdateFailed("daily"), "Failed to update cache for key: daily"),
            (.apiSyncFailed(wrappedError("offline")), "Failed to sync with server: offline"),
            (.configurationLoadFailed(wrappedError("bad config")), "Failed to load dashboard configuration: bad config"),
            (.metricsSaveFailed(wrappedError("write failed")), "Failed to save dashboard metrics: write failed")
        ]

        for (error, expected) in cases {
            #expect(error.errorDescription == expected)
        }
    }

    @Test("errorDescription: covers metric and scale cases")
    func errorDescriptionMetricAndScale() {
        let cases: [(DashboardError, String)] = [
            (.invalidMetricData("bmi"), "Invalid data for metric: bmi"),
            (.metricConversionFailed("weight"), "Failed to convert metric: weight"),
            (.unsupportedMetricType("bone"), "Unsupported metric type: bone"),
            (.scaleDetectionFailed(wrappedError("unknown scale")), "Failed to detect scale type: unknown scale"),
            (.unsupportedScaleType("legacy"), "Unsupported scale type: legacy")
        ]

        for (error, expected) in cases {
            #expect(error.errorDescription == expected)
        }
    }

    @Test("errorDescription: covers graph and goal cases")
    func errorDescriptionGraphAndGoal() {
        let cases: [(DashboardError, String)] = [
            (.chartDataGenerationFailed("empty data"), "Failed to generate chart data: empty data"),
            (.invalidTimeRange("future"), "Invalid time range: future"),
            (.scrollPositionUpdateFailed("missing anchor"), "Failed to update scroll position: missing anchor"),
            (.goalCalculationFailed("division by zero"), "Failed to calculate goal progress: division by zero"),
            (.weightlessSettingsInvalid("missing anchor"), "Invalid weightless settings: missing anchor"),
            (.unitConversionFailed("unsupported unit"), "Failed to convert units: unsupported unit")
        ]

        for (error, expected) in cases {
            #expect(error.errorDescription == expected)
        }
    }

    @Test("recoverySuggestion: returns case-specific suggestions and default fallback")
    func recoverySuggestionBranches() {
        #expect(DashboardError.noActiveAccount.recoverySuggestion == "Please log in to your account and try again.")
        #expect(DashboardError.noEntriesFound.recoverySuggestion == "Try selecting a different time period or add some entries.")
        #expect(DashboardError.apiSyncFailed(wrappedError("offline")).recoverySuggestion == "Check your internet connection and try again.")
        #expect(DashboardError.configurationLoadFailed(wrappedError("bad")).recoverySuggestion == "Try refreshing the dashboard or restart the app.")
        #expect(DashboardError.scaleDetectionFailed(wrappedError("unknown")).recoverySuggestion == "Ensure your scale is properly connected and try again.")
        #expect(DashboardError.invalidMetricData("bmi").recoverySuggestion == "The metric data may be corrupted. Try refreshing the dashboard.")
        #expect(DashboardError.goalCalculationFailed("bad goal").recoverySuggestion == "Check your goal settings and try again.")
        #expect(DashboardError.unitConversionFailed("bad unit").recoverySuggestion == "Please try again or contact support if the problem persists.")
    }

    @Test("failureReason: returns targeted reasons and nil for unsupported branches")
    func failureReasonBranches() {
        #expect(DashboardError.dataLoadingFailed(wrappedError("db")).failureReason == "The dashboard data could not be loaded from the database.")
        #expect(DashboardError.apiSyncFailed(wrappedError("api")).failureReason == "The server request failed or timed out.")
        #expect(DashboardError.invalidMetricData("bmi").failureReason == "The metric data format is incorrect or missing required fields.")
        #expect(DashboardError.scaleDetectionFailed(wrappedError("scale")).failureReason == "The scale type could not be determined from the device information.")
        #expect(DashboardError.chartDataGenerationFailed("empty").failureReason == "The chart data could not be generated from the available entries.")
        #expect(DashboardError.goalCalculationFailed("bad").failureReason == "The goal progress calculation failed due to invalid input data.")
        #expect(DashboardError.noActiveAccount.failureReason == nil)
        #expect(DashboardError.unitConversionFailed("bad").failureReason == nil)
    }

    @Test("equatable: same cases and values compare equal")
    func equatableSameCases() {
        #expect(DashboardError.noActiveAccount == .noActiveAccount)
        #expect(DashboardError.noEntriesFound == .noEntriesFound)
        #expect(DashboardError.cacheUpdateFailed("daily") == .cacheUpdateFailed("daily"))
        #expect(DashboardError.invalidMetricData("bmi") == .invalidMetricData("bmi"))
        #expect(DashboardError.dataLoadingFailed(wrappedError("db")) == .dataLoadingFailed(wrappedError("db")))
        #expect(DashboardError.apiSyncFailed(wrappedError("offline")) == .apiSyncFailed(wrappedError("offline")))
    }

    @Test("equatable: differing values or cases compare unequal")
    func equatableDifferentCases() {
        #expect(DashboardError.cacheUpdateFailed("daily") != .cacheUpdateFailed("monthly"))
        #expect(DashboardError.invalidMetricData("bmi") != .metricConversionFailed("bmi"))
        #expect(DashboardError.dataLoadingFailed(wrappedError("db")) != .dataLoadingFailed(wrappedError("network")))
        #expect(DashboardError.apiSyncFailed(wrappedError("offline")) != .configurationLoadFailed(wrappedError("offline")))
    }

    @Test("equatable: wrapped error cases compare by localized description")
    func equatableWrappedErrorCases() {
        #expect(DashboardError.configurationLoadFailed(wrappedError("bad config")) == .configurationLoadFailed(wrappedError("bad config")))
        #expect(DashboardError.metricsSaveFailed(wrappedError("write failed")) == .metricsSaveFailed(wrappedError("write failed")))
        #expect(DashboardError.scaleDetectionFailed(wrappedError("unknown scale")) == .scaleDetectionFailed(wrappedError("unknown scale")))
        #expect(DashboardError.metricsSaveFailed(wrappedError("write failed")) != .metricsSaveFailed(wrappedError("different write failed")))
    }

    @Test("log: noActiveAccount logs at info severity with default tag")
    func logNoActiveAccountUsesInfoSeverity() {
        let logger = MockLoggerService()

        DashboardError.noActiveAccount.log(with: logger)

        #expect(logger.entries.count == 1)
        #expect(logger.entries[0].level == .info)
        #expect(logger.entries[0].tag == "DashboardError")
        #expect(logger.entries[0].message == DashboardError.noActiveAccount.errorDescription!)
    }

    @Test("log: invalidMetricData logs at error severity with custom tag")
    func logInvalidMetricDataUsesErrorSeverity() {
        let logger = MockLoggerService()
        let error = DashboardError.invalidMetricData("bmi")

        error.log(with: logger, tag: "DashboardTests")

        #expect(logger.entries.count == 1)
        #expect(logger.entries[0].level == .error)
        #expect(logger.entries[0].tag == "DashboardTests")
        #expect(logger.entries[0].message == error.errorDescription!)
    }

    @Test("log: dataLoadingFailed logs mapped dashboard description at error severity")
    func logDataLoadingFailedUsesMappedDescription() {
        let logger = MockLoggerService()
        let error = DashboardError.dataLoadingFailed(wrappedError("No active account"))

        error.log(with: logger, tag: "DashboardDataLayer")

        #expect(logger.entries.count == 1)
        #expect(logger.entries[0].level == .error)
        #expect(logger.entries[0].tag == "DashboardDataLayer")
        #expect(logger.entries[0].message == "Failed to load dashboard data: No active account")
    }

    @Test("result logError: failure logs and success does not")
    func resultLogErrorBehavior() {
        let logger = MockLoggerService()

        let failure: Result<Void, DashboardError> = .failure(.goalCalculationFailed("bad goal"))
        failure.logError(with: logger, tag: "DashboardResultTests")

        let success: Result<Void, DashboardError> = .success(())
        success.logError(with: logger, tag: "DashboardResultTests")

        #expect(logger.entries.count == 1)
        #expect(logger.entries[0].tag == "DashboardResultTests")
        #expect(logger.entries[0].message == DashboardError.goalCalculationFailed("bad goal").errorDescription!)
    }
}
