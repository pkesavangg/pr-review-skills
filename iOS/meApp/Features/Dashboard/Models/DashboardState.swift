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
    var loaderOverride: LoaderModel? = nil
    var alertData: AlertModel? = nil
    var isEditMode: Bool = false
    var selectedMetricLabel: String? = nil
    var gridLayoutId = UUID()
    var isGoalCardRemoved: Bool = false

    // Drag & Drop State
    var draggingMetric: MetricItem? = nil
    var draggingStreak: MetricItem? = nil
    var dropHoverId: String? = nil

    var isAnyItemBeingDragged: Bool {
        draggingMetric != nil || draggingStreak != nil
    }

    mutating func resetDragState() {
        draggingMetric = nil
        draggingStreak = nil
        dropHoverId = nil
        gridLayoutId = UUID()
    }
}

// MARK: - Metrics State
struct MetricsState {
    var metricType: DashboardMetricType = .four
    var metrics: [MetricItem] = []
    var activeMetricsCount: Int = 12

    var metricsToShow: [MetricItem] {
        if metricType == .four {
            let fourLabels: Set<String> = Set(DashboardConstants.MetricType.fourScaleMetrics.compactMap { apiName in
                // Convert API names to display labels
                switch apiName {
                case "bmi": return DashboardStrings.bmi
                case "bodyFat": return DashboardStrings.bodyFat
                case "muscleMass": return DashboardStrings.muscle
                case "water": return DashboardStrings.water
                default: return ""
                }
            })
            return Array(metrics.prefix(activeMetricsCount)).filter { fourLabels.contains($0.label) }
        } else {
            // For 12-metric mode, show all metrics regardless of activeMetricsCount
            return metrics
        }
    }
    
    /// Returns grid columns configuration based on metric type
    var gridColumns: [GridItem] {
        let columnCount = metricType == .four ? 
            DashboardConstants.UI.fourMetricGridColumns : 
            DashboardConstants.UI.twelveMetricGridColumns
        
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
