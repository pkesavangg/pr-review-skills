import Foundation

/// Protocol defining the interface for dashboard cache management operations.
/// Handles caching of operations, chart data, and other performance-critical data.
@MainActor
protocol DashboardCacheManagerProtocol {
    
    // MARK: - Continuous Operations Cache
    
    /// Gets cached continuous operations for a given period, or recalculates if cache is invalid.
    /// - Parameters:
    ///   - period: The time period to get operations for.
    ///   - getOperations: Closure to calculate operations if cache is invalid.
    /// - Returns: Array of cached or newly calculated operations.
    func getContinuousOperations(
        for period: TimePeriod,
        getOperations: () -> [BathScaleWeightSummary]
    ) -> [BathScaleWeightSummary]
    
    /// Invalidates the continuous operations cache.
    func invalidateContinuousOperationsCache()
    
    // MARK: - Visible Operations Cache
    
    /// Gets cached visible operations, or calculates if cache is invalid.
    /// - Parameters:
    ///   - isScrolling: Whether the user is currently scrolling.
    ///   - getVisibleOperations: Closure to calculate visible operations if cache is invalid.
    /// - Returns: Array of visible operations.
    func getVisibleOperations(
        isScrolling: Bool,
        getVisibleOperations: () -> [BathScaleWeightSummary]
    ) -> [BathScaleWeightSummary]
    
    // MARK: - Chart Series Cache
    
    /// Gets cached chart series data, or calculates if cache is invalid.
    /// - Parameters:
    ///   - isScrolling: Whether the user is currently scrolling.
    ///   - isProcessingScrollEnd: Whether scroll end is being processed.
    ///   - period: The current time period.
    ///   - selectedMetric: The currently selected metric label.
    ///   - operationsCount: The count of operations.
    ///   - yAxisDomain: The current Y-axis domain.
    ///   - getChartSeries: Closure to calculate chart series if cache is invalid.
    /// - Returns: Array of chart series data.
    func getChartSeriesData( // swiftlint:disable:this function_parameter_count
        isScrolling: Bool,
        isProcessingScrollEnd: Bool,
        period: TimePeriod,
        selectedMetric: String?,
        operationsCount: Int,
        yAxisDomain: ClosedRange<Double>?,
        getChartSeries: () -> [GraphSeries]
    ) -> [GraphSeries]
    
    /// Invalidates the chart series cache.
    func invalidateChartSeriesCache()
    
    // MARK: - Label Date Range Cache
    
    /// Gets cached operations for label date range, or calculates if cache is invalid.
    /// - Parameters:
    ///   - period: The time period.
    ///   - scrollPosition: The current scroll position.
    ///   - getOperations: Closure to calculate operations if cache is invalid.
    /// - Returns: Result containing operations and updated cache values.
    func getLabelDateRangeOperations(
        period: TimePeriod,
        scrollPosition: Date?,
        getOperations: () -> DateRangeOperationsResult
    ) -> DateRangeOperationsResult
    
    // MARK: - UserDefaults Cache
    
    /// Gets a boolean value from UserDefaults for the given key.
    /// - Parameter key: The UserDefaults key.
    /// - Returns: The boolean value, or false if not set.
    func getBool(forKey key: String) -> Bool
    
    /// Sets a boolean value in UserDefaults for the given key.
    /// - Parameters:
    ///   - value: The boolean value to set.
    ///   - key: The UserDefaults key.
    func setBool(_ value: Bool, forKey key: String)
    
    // MARK: - Cache Clearing
    
    /// Clears all performance caches.
    func clearAllCaches()
}
