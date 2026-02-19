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
    var hasLoadedDashboardConfig: Bool = false // Flag to track when body metrics config is loaded from API
    var hasLoadedProgressMetrics: Bool = false // Flag to track when progress metrics (goal card + streaks) are loaded
    var hasLoadedMetricValues: Bool = false // Flag to track when actual metric values are loaded (not placeholders)
    var loaderOverride: LoaderModel? = nil
    var alertData: AlertModel? = nil
    var isEditMode: Bool = false
    var selectedMetricLabel: String? = nil
    var gridLayoutId = UUID()
    var isGoalCardRemoved: Bool = false
    var isResettingDashboard: Bool = false // Flag to suppress UI updates during reset

    var removedMetrics: Set<String> = [] 
    var removedStreaks: Set<String> = [] 
    
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
    }
}

// MARK: - Metrics State
struct MetricsState {
    var dashboardType: DashboardType = .dashboard12
    var metrics: [MetricItem] = []
    var activeMetricsCount: Int = 12
    var removedMetrics: Set<String> = []

    var metricsToShow: [MetricItem] {
        // Show only active metrics based on activeMetricsCount
        return Array(metrics.prefix(activeMetricsCount))
    }

    /// Returns grid columns configuration based on dashboard type
    var gridColumns: [GridItem] {
        // Columns strictly follow dashboard type
        let columnCount: Int = (dashboardType == .dashboard4)
            ? DashboardConstants.UI.fourMetricGridColumns
            : DashboardConstants.UI.twelveMetricGridColumns
        return Array(repeating: GridItem(.flexible(), spacing: DashboardConstants.UI.gridSpacing), count: columnCount)
    }
}

// MARK: - Streak State
struct StreakState {
    var streakItems: [MetricItem] = []
    var activeStreakItemsCount: Int = 6
    var removedStreaks: Set<String> = []

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

    // Graph readiness state - false until initial computation settles
    var isGraphReady: Bool = false

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
    var hasGoalSet: Bool = false // Track if goal was actually set (not null from API)
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
