import Foundation
import SwiftUI

/// Protocol defining streak management operations
protocol DashboardStreakManaging {
    func refreshStreakData() async throws
    func updateStreakItems(with progress: Progress) async throws
    func resetStreakData() async throws
    func getStreakItemsToShow(isEditMode: Bool) -> [MetricItem]
    func toggleStreakVisibility(at index: Int) async throws
    func calculateStreakAnalytics() -> StreakAnalytics
}

/// Manages all streak calculations and progress tracking for the dashboard
@MainActor
class DashboardStreakManager: ObservableObject, DashboardStreakManaging {

    // MARK: - Dependencies
    @Injector private var entryService: EntryService
    @Injector private var logger: LoggerService

    // MARK: - Published Properties
    @Published var state: StreakState

    // MARK: - Private Properties
    private let originalStreakItems: [(value: String, label: String, unit: String?, preLabel: String?, icon: String?)] = [
        (DashboardStrings.placeholder, DashboardStrings.currentStreak, nil, nil, AppAssets.streak),
        (DashboardStrings.placeholder, DashboardStrings.longestStreak, nil, nil, AppAssets.longestStreak),
        (DashboardStrings.placeholder, DashboardStrings.lbsWeek, nil, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.lbsMonth, nil, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.lbsYear, nil, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.lbsTotal, nil, nil, nil)
    ]

    // MARK: - Initialization
    init(initialState: StreakState = StreakState()) {
        self.state = initialState
        setupInitialStreakItems()
    }

    // MARK: - Setup Methods
    private func setupInitialStreakItems() {
        state.streakItems = originalStreakItems.map {
            MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon)
        }
        state.activeStreakItemsCount = originalStreakItems.count
    }

    // MARK: - Streak Data Management
    func refreshStreakData() async throws {
        do {
            let progress = try await entryService.getProgress()
            try await updateStreakItems(with: progress)
            logger.log(level: .info, tag: "DashboardStreakManager", message: "Refreshed streak data successfully")
        } catch {
            logger.log(level: .error, tag: "DashboardStreakManager", message: "Failed to refresh streak data: \(error)")
            throw DashboardError.dataLoadingFailed(error)
        }
    }

    func updateStreakItems(with progress: Progress) async throws {
        do {
            var updatedStreakItems: [MetricItem] = []

            // Current streak
            updatedStreakItems.append(MetricItem(
                value: "\(progress.currentStreak)",
                label: DashboardStrings.currentStreak,
                unit: nil,
                preLabel: nil,
                icon: AppAssets.streak
            ))

            // Longest streak
            updatedStreakItems.append(MetricItem(
                value: "\(progress.longestStreak)",
                label: DashboardStrings.longestStreak,
                unit: nil,
                preLabel: nil,
                icon: AppAssets.longestStreak
            ))

            // Weekly change (divide by 10 for proper display)
            let weeklyValue = (Double(progress.week) / 10.0)
            updatedStreakItems.append(MetricItem(
                value: formatWeightChange(weeklyValue),
                label: DashboardStrings.lbsWeek,
                unit: nil,
                preLabel: nil,
                icon: nil
            ))

            // Monthly change (divide by 10 for proper display)
            let monthlyValue = (Double(progress.month) / 10.0)
            updatedStreakItems.append(MetricItem(
                value: formatWeightChange(monthlyValue),
                label: DashboardStrings.lbsMonth,
                unit: nil,
                preLabel: nil,
                icon: nil
            ))

            // Yearly change (divide by 10 for proper display)
            let yearlyValue = (Double(progress.year) / 10.0)
            updatedStreakItems.append(MetricItem(
                value: formatWeightChange(yearlyValue),
                label: DashboardStrings.lbsYear,
                unit: nil,
                preLabel: nil,
                icon: nil
            ))

            // Total change (divide by 10 for proper display)
            let totalValue = (Double(progress.total ?? 0) / 10.0)
            updatedStreakItems.append(MetricItem(
                value: formatWeightChange(totalValue),
                label: DashboardStrings.lbsTotal,
                unit: nil,
                preLabel: nil,
                icon: nil
            ))

            // Update streak items array
            state.streakItems = updatedStreakItems
            state.activeStreakItemsCount = updatedStreakItems.count

            logger.log(level: .info, tag: "DashboardStreakManager", message: "Updated streak items with progress data: currentStreak=\(progress.currentStreak), longestStreak=\(progress.longestStreak), week=\(progress.week), month=\(progress.month), year=\(progress.year), total=\(progress.total ?? 0)")

        } catch {
            logger.log(level: .error, tag: "DashboardStreakManager", message: "Failed to update streak items: \(error)")
            throw DashboardError.invalidMetricData("Failed to update streak items with progress data")
        }
    }

    func resetStreakData() async throws {
        setupInitialStreakItems()
        logger.log(level: .info, tag: "DashboardStreakManager", message: "Streak data reset to defaults")
    }

    // MARK: - Streak Item Management
    func getStreakItemsToShow(isEditMode: Bool) -> [MetricItem] {
        if isEditMode {
            return state.streakItems
        } else {
            return Array(state.streakItems.prefix(state.activeStreakItemsCount))
        }
    }

    func toggleStreakVisibility(at index: Int) async throws {
        guard index < state.streakItems.count else {
            throw DashboardError.invalidMetricData("Invalid streak index: \(index)")
        }

        let item = state.streakItems[index]
        let isCurrentlyRemoved = index >= state.activeStreakItemsCount

        state.streakItems.remove(at: index)

        if isCurrentlyRemoved {
            state.streakItems.insert(item, at: state.activeStreakItemsCount)
            state.activeStreakItemsCount += 1
        } else {
            state.streakItems.append(item)
            state.activeStreakItemsCount -= 1
        }

        logger.log(level: .info, tag: "DashboardStreakManager", message: "Toggled streak visibility at index: \(index)")
    }

    func isStreakRemoved(at index: Int) -> Bool {
        guard index < state.streakItems.count else { return false }
        return index >= state.activeStreakItemsCount
    }

    // MARK: - Streak Analytics
    func calculateStreakAnalytics() -> StreakAnalytics {
        let currentStreak = extractStreakValue(for: DashboardStrings.currentStreak)
        let longestStreak = extractStreakValue(for: DashboardStrings.longestStreak)
        let weeklyChange = extractWeightChange(for: DashboardStrings.lbsWeek)
        let monthlyChange = extractWeightChange(for: DashboardStrings.lbsMonth)
        let yearlyChange = extractWeightChange(for: DashboardStrings.lbsYear)
        let totalChange = extractWeightChange(for: DashboardStrings.lbsTotal)

        let trend = calculateStreakTrend(currentStreak: currentStreak, longestStreak: longestStreak)
        let momentum = calculateMomentum(weeklyChange: weeklyChange, monthlyChange: monthlyChange)

        return StreakAnalytics(
            currentStreak: currentStreak,
            longestStreak: longestStreak,
            weeklyChange: weeklyChange,
            monthlyChange: monthlyChange,
            yearlyChange: yearlyChange,
            totalChange: totalChange,
            trend: trend,
            momentum: momentum
        )
    }

    // MARK: - Helper Methods
    private func formatWeightChange(_ value: Double) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 1
        formatter.maximumFractionDigits = 1
        formatter.positivePrefix = "+"

        return formatter.string(from: NSNumber(value: value)) ?? String(format: "%.1f", value)
    }

    private func extractStreakValue(for label: String) -> Int {
        guard let item = state.streakItems.first(where: { $0.label == label }) else { return 0 }
        return Int(item.value) ?? 0
    }

    private func extractWeightChange(for label: String) -> Double {
        guard let item = state.streakItems.first(where: { $0.label == label }) else { return 0.0 }
        // Remove the + sign if present and convert to double
        let cleanValue = item.value.replacingOccurrences(of: "+", with: "")
        return Double(cleanValue) ?? 0.0
    }

    private func calculateStreakTrend(currentStreak: Int, longestStreak: Int) -> StreakTrend {
        if currentStreak == 0 {
            return .broken
        } else if currentStreak == longestStreak {
            return .record
        } else if currentStreak >= longestStreak / 2 {
            return .building
        } else {
            return .starting
        }
    }

    private func calculateMomentum(weeklyChange: Double, monthlyChange: Double) -> StreakMomentum {
        let weeklyAbs = abs(weeklyChange)
        let monthlyAbs = abs(monthlyChange)

        if weeklyAbs > monthlyAbs / 4 {
            return .accelerating
        } else if weeklyAbs < monthlyAbs / 8 {
            return .slowing
        } else {
            return .steady
        }
    }

    // MARK: - Streak Formatting
    func formatStreakDisplay(_ streak: Int) -> String {
        if streak == 0 {
            return "0 days"
        } else if streak == 1 {
            return "1 day"
        } else {
            return "\(streak) days"
        }
    }

    func formatWeightChangeDisplay(_ change: Double, withUnit unit: String? = nil) -> String {
        let formattedChange = formatWeightChange(change)
        if let unit = unit {
            return "\(formattedChange) \(unit)"
        } else {
            return formattedChange
        }
    }

    // MARK: - Streak State Management
    func shouldShowStreakGrid() -> Bool {
        return !getStreakItemsToShow(isEditMode: false).isEmpty
    }

    func getStreakGridColumns() -> [GridItem] {
        return Array(repeating: GridItem(.flexible(), spacing: DashboardConstants.UI.gridSpacing), count: 2)
    }

    // MARK: - Streak Reordering
    func reorderStreakItems(from source: IndexSet, to destination: Int) async throws {
      state.streakItems.move(fromOffsets: source, toOffset: destination)
      logger.log(level: .info, tag: "DashboardStreakManager", message: "Reordered streak items from \(source) to \(destination)")
    }

    // MARK: - Streak Validation
    func validateStreakData() throws {
        guard !state.streakItems.isEmpty else {
            throw DashboardError.invalidMetricData("No streak items available")
        }

        guard state.activeStreakItemsCount <= state.streakItems.count else {
            throw DashboardError.invalidMetricData("Active streak items count exceeds total items")
        }

        guard state.activeStreakItemsCount >= 0 else {
            throw DashboardError.invalidMetricData("Active streak items count cannot be negative")
        }

        logger.log(level: .info, tag: "DashboardStreakManager", message: "Streak data validation passed")
    }
}

// MARK: - Supporting Types
struct StreakAnalytics {
    let currentStreak: Int
    let longestStreak: Int
    let weeklyChange: Double
    let monthlyChange: Double
    let yearlyChange: Double
    let totalChange: Double
    let trend: StreakTrend
    let momentum: StreakMomentum

    var isOnTrack: Bool {
        return trend != .broken && momentum != .slowing
    }

    var streakRatio: Double {
        guard longestStreak > 0 else { return 0.0 }
        return Double(currentStreak) / Double(longestStreak)
    }
}

enum StreakTrend {
    case broken
    case starting
    case building
    case record

    var description: String {
        switch self {
        case .broken: return "Streak broken"
        case .starting: return "Getting started"
        case .building: return "Building momentum"
        case .record: return "Record streak!"
        }
    }

    var color: Color {
        switch self {
        case .broken: return .red
        case .starting: return .orange
        case .building: return .blue
        case .record: return .green
        }
    }
}

enum StreakMomentum {
    case accelerating
    case steady
    case slowing

    var description: String {
        switch self {
        case .accelerating: return "Accelerating"
        case .steady: return "Steady progress"
        case .slowing: return "Slowing down"
        }
    }

    var icon: String {
        switch self {
        case .accelerating: return "arrow.up.circle.fill"
        case .steady: return "arrow.right.circle.fill"
        case .slowing: return "arrow.down.circle.fill"
        }
    }
}
