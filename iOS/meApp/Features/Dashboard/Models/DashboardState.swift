import SwiftUI
import Foundation

/// Main dashboard state container
struct DashboardState {
    var ui: UIState = UIState()
    var metrics: MetricsState = MetricsState()
    var streak: StreakState = StreakState()
    var graph: GraphState = GraphState()
    var goal: GoalState = GoalState()
    var data: DataState = DataState()
}

// MARK: - UI State
struct UIState {
    var isLoading: Bool = false
    var hasInitializedChart: Bool = false
    var loaderOverride: LoaderModel? = nil
    var alertData: AlertModel? = nil
    var isEditMode: Bool = false
    var selectedMetricLabel: String? = nil
    var gridLayoutId = UUID()
    var isGoalCardRemoved: Bool = false
    
    // Goal card position management (like large widget)
    var goalCardPosition: Int = 0 // Position after divider (0 = first position)
    
    // Streak grid order management - saves the order of streak items as array of IDs
    var streakGridOrder: [String] = [] // Array of MetricItem.id.uuidString to preserve order

    // Drag & Drop State
    var draggingMetric: MetricItem? = nil
    var draggingStreak: MetricItem? = nil
    var isGoalCardBeingDragged: Bool = false
    var dropHoverId: String? = nil

    var isAnyItemBeingDragged: Bool {
        draggingMetric != nil || draggingStreak != nil || isGoalCardBeingDragged
    }

    mutating func resetDragState() {
        draggingMetric = nil
        draggingStreak = nil
        isGoalCardBeingDragged = false
        dropHoverId = nil
        gridLayoutId = UUID()
    }
}

// MARK: - Metrics State
struct MetricsState {
    var dashboardType: DashboardType = .dashboard12
    var metrics: [MetricItem] = []
    var activeMetricsCount: Int = 12

    var metricsToShow: [MetricItem] {
        // Show only active metrics based on activeMetricsCount
        return Array(metrics.prefix(activeMetricsCount))
    }

    /// Returns grid columns configuration based on dashboard type
    var gridColumns: [GridItem] {
        // Force 4 columns on iPad irrespective of dashboard type
        let columnCount: Int
        if DevicePlatform.isTablet {
            columnCount = 4
        } else {
            columnCount = dashboardType == .dashboard4 ?
                DashboardConstants.UI.fourMetricGridColumns :
                DashboardConstants.UI.twelveMetricGridColumns
        }
        return Array(repeating: GridItem(.flexible(), spacing: DashboardConstants.UI.gridSpacing), count: columnCount)
    }
}

// MARK: - Streak State
struct StreakState {
    var streakItems: [MetricItem] = []
    var activeStreakItemsCount: Int = 6

    var streakItemsToShow: [MetricItem] {
        Array(streakItems.prefix(activeStreakItemsCount))
    }
}

// MARK: - Graph State
struct GraphState {
    var selectedEntry: BathScaleOperationDTO? = nil
    var selectedPeriod: TimePeriod = .week
    var xScrollPosition: Date = Date()
    var selectedWeight: Double? = nil
    var selectedPoint: BathScaleWeightSummary? = nil
    var selectedXValue: Date? = nil
    var chartHeight: CGFloat = 0
    var annotationHeight: CGFloat = 0

    // Scroll and interaction state
    var isScrolling: Bool = false
    var showCrosshair: Bool = false
    var scrollEndTimer: Timer? = nil

    // Data change trigger for graph refresh
    var dataChangeTrigger: Int = 0
    var hasDetectedScrollInCurrentGesture: Bool = false

    // Cached Y-axis domain to prevent recalculation during scroll
    var cachedYAxisDomain: ClosedRange<Double>?

    // Cached Y-axis ticks to prevent recalculation during scroll
    var cachedYAxisTicks: [Double]?

    // Cached X-axis values to prevent recalculation during scroll
    var cachedXAxisValues: [Date]?

    mutating func clearSelection() {
        selectedEntry = nil
        selectedPoint = nil
        selectedXValue = nil
        selectedWeight = nil
        showCrosshair = false
    }

    mutating func updateScrollState(isScrolling: Bool) {
        self.isScrolling = isScrolling
        if isScrolling {
            clearSelection()
        }
    }
}

// MARK: - Goal State
struct GoalState {
    var goalType: GoalType = .gain
    var goalStartWeight: Double = 0.0
    var goalWeight: Double = 0.0
    var goalUnit: WeightUnit = .lb
    var goalDelta: Double = 0.0
    var goalProgress: CGFloat = 0.0
}

// MARK: - Data State
struct DataState {
    var dailySummaries: [BathScaleWeightSummary?] = []
    var monthlySummaries: [BathScaleWeightSummary?] = []
    var latestWeightStored: Int = 0

    // Internal caches for fast incremental updates
    var dailyCache: [String: BathScaleWeightSummary] = [:]
    var monthlyCache: [String: BathScaleWeightSummary] = [:]

    var hasAnyEntries: Bool {
        !dailySummaries.isEmpty || !monthlySummaries.isEmpty
    }

    var continuousOperations: [BathScaleWeightSummary] {
        dailySummaries.compactMap { $0 }.sorted { $0.date < $1.date }
    }
}
