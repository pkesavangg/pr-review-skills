import Foundation

/// Implementation of DashboardCacheManagerProtocol for managing dashboard cache operations.
/// Handles caching of operations, chart data, and other performance-critical data.
@MainActor
final class DashboardCacheManager: DashboardCacheManagerProtocol {
    
    // MARK: - Product Type Tracking

    /// The product type that current caches belong to. When this changes,
    /// all caches are invalid because the underlying data source has changed.
    private var _cachedProductType: EntryType?
    /// The baby profile ID for baby selections. Different baby profiles need separate caches.
    private var _cachedBabyProfileId: String?

    /// Updates the product context. Call before accessing caches after a product switch.
    /// If the product context changed, all caches are automatically invalidated.
    func setProductContext(productType: EntryType, babyProfileId: String? = nil) {
        if _cachedProductType != productType || _cachedBabyProfileId != babyProfileId {
            clearAllCaches()
            _cachedProductType = productType
            _cachedBabyProfileId = babyProfileId
        }
    }

    // MARK: - Continuous Operations Cache

    private var _cachedContinuousOperations: [BathScaleWeightSummary] = []
    private var _cachedContinuousPeriod: TimePeriod?

    func getContinuousOperations(
        for period: TimePeriod,
        getOperations: () -> [BathScaleWeightSummary]
    ) -> [BathScaleWeightSummary] {
        // Return cache if valid for current period
        if _cachedContinuousPeriod == period && !_cachedContinuousOperations.isEmpty {
            return _cachedContinuousOperations
        }
        // Recalculate and cache
        _cachedContinuousOperations = getOperations()
        _cachedContinuousPeriod = period
        return _cachedContinuousOperations
    }
    
    func invalidateContinuousOperationsCache() {
        _cachedContinuousOperations = []
        _cachedContinuousPeriod = nil
        // Also invalidate dependent caches
        cachedVisibleOperations = []
        cachedChartSeriesData = nil
        // Invalidate label date range cache
        _cachedLabelDateRangeOps = []
        _cachedLabelDateRangePeriod = nil
        _cachedLabelDateRangeScrollPos = nil
    }
    
    // MARK: - Visible Operations Cache
    
    private var cachedVisibleOperations: [BathScaleWeightSummary] = []
    private var lastVisibleOperationsCacheTime = Date.distantPast
    
    func getVisibleOperations(
        isScrolling: Bool,
        getVisibleOperations: () -> [BathScaleWeightSummary]
    ) -> [BathScaleWeightSummary] {
        // During scrolling, be very aggressive about using cache
        // The slight inaccuracy is worth the CPU savings
        if isScrolling && !cachedVisibleOperations.isEmpty {
            let timeSinceLastCache = Date().timeIntervalSince(lastVisibleOperationsCacheTime)
            // Use cache for up to 250ms during scrolling to significantly reduce CPU
            if timeSinceLastCache < 0.25 {
                return cachedVisibleOperations
            }
        }
        
        // Get fresh result
        let visible = getVisibleOperations()
        
        // Update cache
        cachedVisibleOperations = visible
        lastVisibleOperationsCacheTime = Date()
        
        return visible
    }
    
    // MARK: - Chart Series Cache
    
    private var cachedChartSeriesData: [GraphSeries]?
    private var cachedChartSeriesPeriod: TimePeriod?
    private var cachedChartSeriesMetric: String?
    private var cachedChartSeriesCount: Int = 0
    // Track cached Y-axis domain to detect changes and invalidate metric series cache
    private var lastCachedYAxisDomain: ClosedRange<Double>?
    
    // swiftlint:disable:next function_parameter_count
    func getChartSeriesData(
        isScrolling: Bool,
        isProcessingScrollEnd: Bool,
        period: TimePeriod,
        selectedMetric: String?,
        operationsCount: Int,
        yAxisDomain: ClosedRange<Double>?,
        getChartSeries: () -> [GraphSeries]
    ) -> [GraphSeries] {
        // Skip expensive recalculation during scroll end processing
        guard !isProcessingScrollEnd else {
            return cachedChartSeriesData ?? []
        }
        
        // During scrolling, use cached data ONLY if the metric selection hasn't changed
        // If metric selection changed, recalculate immediately
        if isScrolling, let cached = cachedChartSeriesData, !cached.isEmpty {
            // Compare metric selection - handles both selection and deselection cases
            let metricUnchanged = cachedChartSeriesMetric == selectedMetric
            if metricUnchanged {
                return cached
            }
            // Metric changed → clear cache
            cachedChartSeriesData = nil
            cachedChartSeriesMetric = nil
        }
        
        // Check if cached data is still valid (same period, same data count, same metric, same Y-axis domain)
        if let cached = cachedChartSeriesData,
           !cached.isEmpty,
           cachedChartSeriesPeriod == period,
           cachedChartSeriesMetric == selectedMetric,
           cachedChartSeriesCount == operationsCount,
           lastCachedYAxisDomain == yAxisDomain {
            return cached
        }
        
        // Generate fresh data
        let seriesData = getChartSeries()
        
        // Cache the result with metadata for validation
        cachedChartSeriesData = seriesData
        cachedChartSeriesPeriod = period
        cachedChartSeriesMetric = selectedMetric
        cachedChartSeriesCount = operationsCount
        lastCachedYAxisDomain = yAxisDomain
        
        return seriesData
    }
    
    func invalidateChartSeriesCache() {
        cachedChartSeriesData = nil
        cachedChartSeriesPeriod = nil
        cachedChartSeriesMetric = nil
        cachedChartSeriesCount = 0
        lastCachedYAxisDomain = nil
    }
    
    // MARK: - Label Date Range Cache
    
    private var _cachedLabelDateRangeOps: [BathScaleWeightSummary] = []
    private var _cachedLabelDateRangePeriod: TimePeriod?
    private var _cachedLabelDateRangeScrollPos: Date?
    
    func getLabelDateRangeOperations(
        period: TimePeriod,
        scrollPosition: Date?,
        getOperations: () -> DateRangeOperationsResult
    ) -> DateRangeOperationsResult {
        // Check if cache is valid (same period and scrollPosition)
        if let cachedPeriod = _cachedLabelDateRangePeriod,
           let cachedScrollPos = _cachedLabelDateRangeScrollPos,
           cachedPeriod == period,
           cachedScrollPos == scrollPosition,
           !_cachedLabelDateRangeOps.isEmpty {
            // Return cached result
            return DateRangeOperationsResult(
                operations: _cachedLabelDateRangeOps,
                cachedPeriod: cachedPeriod,
                cachedScrollPos: cachedScrollPos,
                cachedOps: _cachedLabelDateRangeOps
            )
        }
        
        // Cache miss - recalculate
        let result = getOperations()
        
        // Update cache
        _cachedLabelDateRangeOps = result.cachedOps
        _cachedLabelDateRangePeriod = result.cachedPeriod
        _cachedLabelDateRangeScrollPos = result.cachedScrollPos
        
        return result
    }
    
    // MARK: - KvStorage Cache

    func getBool(forKey key: String) -> Bool {
        return KvStorageService.shared.getValue(forKey: key) as? Bool ?? false
    }

    func setBool(_ value: Bool, forKey key: String) {
        KvStorageService.shared.setValue(value, forKey: key)
    }
    
    // MARK: - Cache Clearing
    
    func clearAllCaches() {
        // Clear continuousOperations cache
        _cachedContinuousOperations = []
        _cachedContinuousPeriod = nil
        // Clear chart series cache
        cachedChartSeriesData = nil
        cachedChartSeriesPeriod = nil
        cachedChartSeriesMetric = nil
        lastCachedYAxisDomain = nil
        cachedChartSeriesCount = 0
        // Clear visible operations cache
        cachedVisibleOperations = []
        lastVisibleOperationsCacheTime = Date.distantPast
        // Clear label date range cache
        _cachedLabelDateRangeOps = []
        _cachedLabelDateRangePeriod = nil
        _cachedLabelDateRangeScrollPos = nil
    }
}
