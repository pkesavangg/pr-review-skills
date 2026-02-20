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
    private var hasUpdatedWithRealData: Bool = false
    private var originalStreakItems: [MetricItem] {
        let streakLabels = getStreakLabels()
        return [
            MetricItem(value: DashboardStrings.placeholder, label: DashboardStrings.currentStreak, unit: nil, preLabel: nil, icon: AppAssets.streak),
            MetricItem(value: DashboardStrings.placeholder, label: DashboardStrings.longestStreak, unit: nil, preLabel: nil, icon: AppAssets.longestStreak),
            MetricItem(value: DashboardStrings.placeholder, label: streakLabels.week, unit: nil, preLabel: nil, icon: nil),
            MetricItem(value: DashboardStrings.placeholder, label: streakLabels.month, unit: nil, preLabel: nil, icon: nil),
            MetricItem(value: DashboardStrings.placeholder, label: streakLabels.year, unit: nil, preLabel: nil, icon: nil),
            MetricItem(value: DashboardStrings.placeholder, label: streakLabels.total, unit: nil, preLabel: nil, icon: nil)
        ]
    }

    // MARK: - Initialization
    init(initialState: StreakState = StreakState(), skipInitialSetup: Bool = false) {
        self.state = initialState
        if !skipInitialSetup {
            setupInitialStreakItems()
        }
    }

    // MARK: - Setup Methods
    func setupInitialStreakItems() {
        state.streakItems = originalStreakItems
        state.activeStreakItemsCount = originalStreakItems.count
    }

    // MARK: - Streak Data Management
    func refreshStreakData() async throws {
        do {
            let progress = try await entryService.getProgress()
            try await updateStreakItems(with: progress)
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

            // Resolve unit once
            let weightUnit: WeightUnit = accountService.activeAccount?.weightSettings?.weightUnit ?? .lb

            // Weekly change
            let weeklyValue = Double(progress.week)
            let weeklyDisplay = weightUnit == .kg
                ? ConversionTools.convertStoredToKg(Int(weeklyValue))
                : ConversionTools.convertStoredToLbs(Int(weeklyValue))
            let weeklyUnitLabel = WeightValueConvertor.unitForDisplay(value: abs(weeklyDisplay), unit: weightUnit)
            updatedStreakItems.append(MetricItem(
                value: formatWeightChange(weeklyValue, unit: weightUnit.rawValue),
                label: "\(weeklyUnitLabel)/week",
                unit: nil,
                preLabel: nil,
                icon: nil
            ))

            // Monthly change
            let monthlyValue = Double(progress.month)
            let monthlyDisplay = weightUnit == .kg
                ? ConversionTools.convertStoredToKg(Int(monthlyValue))
                : ConversionTools.convertStoredToLbs(Int(monthlyValue))
            let monthlyUnitLabel = WeightValueConvertor.unitForDisplay(value: abs(monthlyDisplay), unit: weightUnit)
            updatedStreakItems.append(MetricItem(
                value: formatWeightChange(monthlyValue, unit: weightUnit.rawValue),
                label: "\(monthlyUnitLabel)/month",
                unit: nil,
                preLabel: nil,
                icon: nil
            ))

            // Yearly change
            let yearlyValue = Double(progress.year)
            let yearlyDisplay = weightUnit == .kg
                ? ConversionTools.convertStoredToKg(Int(yearlyValue))
                : ConversionTools.convertStoredToLbs(Int(yearlyValue))
            let yearlyUnitLabel = WeightValueConvertor.unitForDisplay(value: abs(yearlyDisplay), unit: weightUnit)
            updatedStreakItems.append(MetricItem(
                value: formatWeightChange(yearlyValue, unit: weightUnit.rawValue),
                label: "\(yearlyUnitLabel)/year",
                unit: nil,
                preLabel: nil,
                icon: nil
            ))

            // Total change
            let totalValue = Double(progress.total ?? 0)
            let totalDisplay = weightUnit == .kg
                ? ConversionTools.convertStoredToKg(Int(totalValue))
                : ConversionTools.convertStoredToLbs(Int(totalValue))
            let totalUnitLabel = WeightValueConvertor.unitForDisplay(value: abs(totalDisplay), unit: weightUnit)
            updatedStreakItems.append(MetricItem(
                value: formatWeightChange(totalValue, unit: weightUnit.rawValue),
                label: "\(totalUnitLabel)/total",
                unit: nil,
                preLabel: nil,
                icon: nil
            ))

            // Update streak items array
            let isFirstUpdate = !hasUpdatedWithRealData
            state.streakItems = updatedStreakItems
            hasUpdatedWithRealData = true

            // Preserve active count only after the first real data update
            if !isFirstUpdate {
                state.activeStreakItemsCount = min(
                    state.activeStreakItemsCount,
                    updatedStreakItems.count
                )
            }

        } catch {
            logger.log(level: .error, tag: "DashboardStreakManager", message: "Failed to update streak items: \(error)")
            throw DashboardError.invalidMetricData("Failed to update streak items with progress data")
        }
    }

    func resetStreakData() async throws {
        // Reset to original order and restore all streaks
        setupInitialStreakItems()
        
        // Ensure all streak items are active after reset
        state.activeStreakItemsCount = state.streakItems.count
        
        // Clear any removal state to ensure all streaks are visible
        state.removedStreaks.removeAll()
        
        // Refresh streak data to get current unit labels
        try await refreshStreakData()
    }
    
    /// Refreshes streak data when unit changes
    func refreshStreakDataForUnitChange() async throws {
        // Re-fetch progress data and update with new unit
        let progress = try await entryService.getProgress()
        try await updateStreakItems(with: progress)
    }

    // MARK: - Streak Item Management
    func getStreakItemsToShow(isEditMode: Bool) -> [MetricItem] {
        if isEditMode {
            return state.streakItems
        } else {
            return Array(state.streakItems.prefix(state.activeStreakItemsCount))
        }
    }
    
    /// Resets the active streak items count to show all streaks (useful for R4 setup)
    func resetActiveStreakItemsCountToShowAll() {
        state.activeStreakItemsCount = state.streakItems.count
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
    private func getStreakLabels() -> StreakLabels {
        let unit = getUnitText()
        return StreakLabels(
            week: "\(unit)/week",
            month: "\(unit)/month",
            year: "\(unit)/year",
            total: "\(unit)/total"
        )
    }
    
    /// Structure to hold streak labels and avoid large tuple
    private struct StreakLabels {
        let week: String
        let month: String
        let year: String
        let total: String
    }
    
    private func formatWeightChange(_ value: Double, unit: String) -> String {
        // Convert stored weight to display unit
        let displayValue: Double
        if unit == "kg" {
            displayValue = ConversionTools.convertStoredToKg(Int(value))
        } else {
            displayValue = ConversionTools.convertStoredToLbs(Int(value))
        }
        
        // Round to one decimal to determine if the displayed value is effectively zero
        let roundedToOneDecimal = ConversionTools.rounded(displayValue, toPlaces: 1)
        
        // If the rounded value is zero, show "0"
        if roundedToOneDecimal == 0 {
            return "0"
        }
        
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 1
        formatter.maximumFractionDigits = 1
        formatter.positivePrefix = "+"
        
        // Use the original displayValue for formatting (formatter will round to 1 decimal)
        return formatter.string(from: NSNumber(value: displayValue)) ?? String(format: "%.1f", roundedToOneDecimal)
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
        let columnCount = DevicePlatform.isTablet ? 4 : 2
        return Array(repeating: GridItem(.flexible(), spacing: DashboardConstants.UI.gridSpacing), count: columnCount)
    }

    // MARK: - Streak Reordering
    func reorderStreakItems(from source: IndexSet, to destination: Int) async throws {
      state.streakItems.move(fromOffsets: source, toOffset: destination)
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
    }
}
