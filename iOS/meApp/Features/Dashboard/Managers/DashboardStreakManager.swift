import Foundation
import SwiftUI

/// Manages all streak calculations and progress tracking for the dashboard
@MainActor
class DashboardStreakManager: ObservableObject, DashboardStreakManaging {

    // MARK: - Dependencies
    @Injector private var entryService: EntryService
    @Injector private var logger: LoggerService
    @Injector private var accountService: AccountService

    // MARK: - Published Properties
    @Published var state: StreakState

    // MARK: - Private Properties
    private var originalStreakItems: [(value: String, label: String, unit: String?, preLabel: String?, icon: String?)] {
        let streakLabels = getStreakLabels()
        return [
            (DashboardStrings.placeholder, DashboardStrings.currentStreak, nil, nil, AppAssets.streak),
            (DashboardStrings.placeholder, DashboardStrings.longestStreak, nil, nil, AppAssets.longestStreak),
            (DashboardStrings.placeholder, streakLabels.week, nil, nil, nil),
            (DashboardStrings.placeholder, streakLabels.month, nil, nil, nil),
            (DashboardStrings.placeholder, streakLabels.year, nil, nil, nil),
            (DashboardStrings.placeholder, streakLabels.total, nil, nil, nil)
        ]
    }

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

            // Weekly change
            let weeklyValue = Double(progress.week)
            updatedStreakItems.append(MetricItem(
                value: formatWeightChange(weeklyValue, unit: getUnitText()),
                label: getStreakLabels().week,
                unit: nil,
                preLabel: nil,
                icon: nil
            ))

            // Monthly change
            let monthlyValue = Double(progress.month)
            updatedStreakItems.append(MetricItem(
                value: formatWeightChange(monthlyValue, unit: getUnitText()),
                label: getStreakLabels().month,
                unit: nil,
                preLabel: nil,
                icon: nil
            ))

            // Yearly change
            let yearlyValue = Double(progress.year)
            updatedStreakItems.append(MetricItem(
                value: formatWeightChange(yearlyValue, unit: getUnitText()),
                label: getStreakLabels().year,
                unit: nil,
                preLabel: nil,
                icon: nil
            ))

            // Total change
            let totalValue = Double(progress.total ?? 0)
            updatedStreakItems.append(MetricItem(
                value: formatWeightChange(totalValue, unit: getUnitText()),
                label: getStreakLabels().total,
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
        // Refresh streak data to get current unit labels
        try await refreshStreakData()
        logger.log(level: .info, tag: "DashboardStreakManager", message: "Streak data reset to defaults")
    }
    
    /// Refreshes streak data when unit changes
    func refreshStreakDataForUnitChange() async throws {
        // Re-fetch progress data and update with new unit
        let progress = try await entryService.getProgress()
        try await updateStreakItems(with: progress)
        logger.log(level: .info, tag: "DashboardStreakManager", message: "Refreshed streak data for unit change")
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
        let weeklyChange = extractWeightChange(for: getStreakLabels().week)
        let monthlyChange = extractWeightChange(for: getStreakLabels().month)
        let yearlyChange = extractWeightChange(for: getStreakLabels().year)
        let totalChange = extractWeightChange(for: getStreakLabels().total)

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
    
    /// Returns the current weight unit as a string (e.g., "lbs" or "kg")
    private func getUnitText() -> String {
        return accountService.activeAccount?.weightSettings?.weightUnit?.rawValue ?? "lbs"
    }
    
    /// Returns dynamic streak labels based on current unit
    private func getStreakLabels() -> (week: String, month: String, year: String, total: String) {
        let unit = getUnitText()
        return (
            week: "\(unit)/week",
            month: "\(unit)/month", 
            year: "\(unit)/year",
            total: "\(unit)/total"
        )
    }
    
    private func formatWeightChange(_ value: Double, unit: String) -> String {
        // Convert stored weight to display unit
        let displayValue: Double
        if unit == "kg" {
            displayValue = ConversionTools.convertStoredToKg(Int(value))
        } else {
            displayValue = ConversionTools.convertStoredToLbs(Int(value))
        }
        
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 1
        formatter.maximumFractionDigits = 1
        formatter.positivePrefix = "+"

        return formatter.string(from: NSNumber(value: displayValue)) ?? String(format: "%.1f", displayValue)
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
        let formattedChange = formatWeightChange(change, unit: "")
        return formattedChange
    }

    // MARK: - Streak State Management
    func shouldShowStreakGrid() -> Bool {
        return !getStreakItemsToShow(isEditMode: false).isEmpty
    }

    func getStreakGridColumns() -> [GridItem] {
        // Always use 4 columns on iPad
        let columnCount = DevicePlatform.isTablet ? 4 : DashboardConstants.UI.streakGridColumns
        return Array(repeating: GridItem(.flexible(), spacing: DashboardConstants.UI.gridSpacing), count: columnCount)
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

