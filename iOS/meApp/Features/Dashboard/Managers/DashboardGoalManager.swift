import Foundation
import SwiftUI

/// Manages all goal tracking and calculation operations for the dashboard
@MainActor
class DashboardGoalManager: ObservableObject, DashboardGoalManaging {

    // MARK: - Dependencies
    @Injector private var accountService: AccountService
    @Injector private var entryService: EntryService
    @Injector private var logger: LoggerService

    // MARK: - Published Properties
    @Published var state: GoalState

    // MARK: - Initialization
    init(initialState: GoalState = GoalState()) {
        self.state = initialState

    }

    // MARK: - Goal Data Loading
    func loadGoalData() async throws {
        do {
            guard let account = accountService.activeAccount else {
                throw DashboardError.noActiveAccount
            }

            guard let goalSettings = account.goalSettings else {
                return
            }

            // Extract all relationship data BEFORE async call (R7)
            let goalType = goalSettings.goalType ?? .gain
            let goalUnit = account.weightSettings?.weightUnit ?? .lb
            let hasGoalSet = goalSettings.goalWeight != nil
            let initialWeightStored = Int(goalSettings.initialWeight ?? 0)
            let goalWeightStored = Int(goalSettings.goalWeight ?? 0)

            // Get current weight from latest entry
            let latestEntry = try await entryService.getLatestEntry()
            let currentWeightStored = latestEntry?.scaleEntry?.weight ?? 0

            // Update goal state with extracted settings
            state.goalType = goalType
            state.goalUnit = goalUnit
            state.hasGoalSet = hasGoalSet

            let initialWeightDisplay = convertStoredWeightToDisplay(initialWeightStored)
            let goalWeightDisplay = convertStoredWeightToDisplay(goalWeightStored)
            let currentWeightDisplay = convertStoredWeightToDisplay(currentWeightStored)

            // Set basic goal data
            state.goalStartWeight = initialWeightDisplay
            state.goalWeight = goalWeightDisplay
            state.goalDelta = goalWeightDisplay - currentWeightDisplay

            // Calculate progress
            let totalDistance = abs(state.goalWeight - state.goalStartWeight)
            let achievedDistance = abs(currentWeightDisplay - state.goalStartWeight)

            if totalDistance > 0 {
                let progress = min(max(CGFloat(achievedDistance / totalDistance), 0), 1)
                state.goalProgress = progress
            } else {
                state.goalProgress = 1.0
            }

        } catch let error as DashboardError {
            throw error
        } catch {
            logger.log(level: .error, tag: "DashboardGoalManager", message: "Failed to load goal data: \(error)")
            throw DashboardError.goalCalculationFailed("Failed to load goal data: \(error.localizedDescription)")
        }
    }

    // MARK: - Goal Progress Updates
    func updateGoalProgress(currentWeight: Int) async throws {
        do {
            guard let account = accountService.activeAccount else {
                throw DashboardError.noActiveAccount
            }

            guard let goalSettings = account.goalSettings else {
                return
            }

            // Convert weights to display units
            let initialWeightStored = Int(goalSettings.initialWeight ?? 0)
            let goalWeightStored = Int(goalSettings.goalWeight ?? 0)

            let initialWeightDisplay = convertStoredWeightToDisplay(initialWeightStored)
            let goalWeightDisplay = convertStoredWeightToDisplay(goalWeightStored)
            let currentWeightDisplay = convertStoredWeightToDisplay(currentWeight)

            // Update goal state
            state.goalStartWeight = initialWeightDisplay
            state.goalWeight = goalWeightDisplay
            state.goalDelta = currentWeightDisplay - initialWeightDisplay

            // Recalculate progress
            let totalDistance = abs(state.goalWeight - state.goalStartWeight)
            let achievedDistance = abs(state.goalDelta)

            if totalDistance > 0 {
                let progress = min(max(CGFloat(achievedDistance / totalDistance), 0), 1)
                state.goalProgress = progress
            } else {
                state.goalProgress = 1.0
            }

        } catch let error as DashboardError {
            throw error
        } catch {
            logger.log(level: .error, tag: "DashboardGoalManager", message: "Failed to update goal progress: \(error)")
            throw DashboardError.goalCalculationFailed("Failed to update goal progress: \(error.localizedDescription)")
        }
    }
    
    /// Refreshes goal data when unit changes
    func refreshGoalDataForUnitChange() async throws {
        // Re-load goal data with new unit
        try await loadGoalData()
    }

    // MARK: - Weightless Mode Support
    func calculateWeightlessGoal(anchorWeight: Double) async throws {
        do {
            guard let account = accountService.activeAccount else {
                throw DashboardError.noActiveAccount
            }

            guard let goalSettings = account.goalSettings else {
                return
            }

            // Extract all relationship data BEFORE async call (R7)
            let initialWeightStored = Int(goalSettings.initialWeight ?? 0)
            let goalWeightStored = Int(goalSettings.goalWeight ?? 0)

            // Get current weight from latest entry
            let latestEntry = try await entryService.getLatestEntry()
            let currentWeightStored = latestEntry?.scaleEntry?.weight ?? 0

            // Convert weights to display units
            let initialWeightDisplay = convertStoredWeightToDisplay(initialWeightStored)
            let goalWeightDisplay = convertStoredWeightToDisplay(goalWeightStored)
            let currentWeightDisplay = convertStoredWeightToDisplay(currentWeightStored)

            // Calculate weightless mode values (differences from anchor weight)
            state.goalStartWeight = initialWeightDisplay - anchorWeight
            state.goalWeight = goalWeightDisplay - anchorWeight
            state.goalDelta = currentWeightDisplay - anchorWeight

            // Recalculate progress based on weightless-adjusted values
            let totalDistance = abs(state.goalWeight - state.goalStartWeight)
            let achievedDistance = abs(state.goalDelta)

            if totalDistance > 0 {
                let progress = min(max(CGFloat(achievedDistance / totalDistance), 0), 1)
                state.goalProgress = progress
            } else {
                state.goalProgress = 1.0
            }

        } catch let error as DashboardError {
            throw error
        } catch {
            logger.log(level: .error, tag: "DashboardGoalManager", message: "Failed to calculate weightless goal: \(error)")
            throw DashboardError.goalCalculationFailed("Failed to calculate weightless goal: \(error.localizedDescription)")
        }
    }

    // MARK: - Goal Display Methods
    func getGoalWeightForDisplay(isWeightlessMode: Bool, anchorWeight: Double?) -> Double? {
        // Return nil if no goal is set (API returned null)
        guard state.hasGoalSet else { return nil }
        
        if isWeightlessMode {
            guard let anchorWeight = anchorWeight else { return state.goalWeight }
            return state.goalWeight - anchorWeight
        } else {
            return state.goalWeight
        }
    }

    func formatGoalProgress() -> String {
        let percentage = Int(state.goalProgress * 100)
        return "\(percentage)%"
    }

    // MARK: - Goal Validation
    func validateGoalSettings() throws {
        guard let account = accountService.activeAccount else {
            throw DashboardError.noActiveAccount
        }

        guard let goalSettings = account.goalSettings else {
            throw DashboardError.goalCalculationFailed("No goal settings configured")
        }

        guard let initialWeight = goalSettings.initialWeight, initialWeight > 0 else {
            throw DashboardError.goalCalculationFailed("Invalid initial weight")
        }

        guard let goalWeight = goalSettings.goalWeight, goalWeight > 0 else {
            throw DashboardError.goalCalculationFailed("Invalid goal weight")
        }

        // Validate goal type consistency
        let weightDifference = goalWeight - initialWeight
        switch goalSettings.goalType {
        case .gain:
            if weightDifference <= 0 {
                throw DashboardError.goalCalculationFailed("Goal weight must be higher than initial weight for gain goals")
            }
//        case .loss:
//            if weightDifference >= 0 {
//                throw DashboardError.goalCalculationFailed("Goal weight must be lower than initial weight for loss goals")
//            }
        default:
            break
        }

    }

    // MARK: - Goal Analytics
    func calculateGoalAnalytics() -> GoalAnalytics {
        let daysToGoal = calculateDaysToGoal()
        let weeklyTarget = calculateWeeklyTarget()
        let currentTrend = calculateCurrentTrend()

        return GoalAnalytics(
            daysToGoal: daysToGoal,
            weeklyTarget: weeklyTarget,
            currentTrend: currentTrend,
            progressPercentage: Double(state.goalProgress * 100)
        )
    }

    private func calculateDaysToGoal() -> Int? {
        // This would calculate estimated days to reach goal based on current progress
        // For now, return nil to indicate calculation not available
        return nil
    }

    private func calculateWeeklyTarget() -> Double? {
        // This would calculate weekly weight change target
        // For now, return nil to indicate calculation not available
        return nil
    }

    private func calculateCurrentTrend() -> GoalTrend {
        // This would analyze current progress trend
        // For now, return neutral trend
        return .neutral
    }

    // MARK: - Weight Conversion Helpers
    func convertWeightToDisplay(_ storedWeight: Int) -> Double {
        return convertStoredWeightToDisplay(storedWeight)
    }

    func formatWeightForDisplay(_ weight: Double, isWeightlessMode: Bool) -> String {
        // Round to 1 decimal place using Decimal to avoid binary floating-point artifacts
        let decimal = Decimal(weight)
        let roundedDecimal = (decimal as NSDecimalNumber)
            .rounding(accordingToBehavior: NSDecimalNumberHandler(
                roundingMode: .plain,
                scale: 1,
                raiseOnExactness: false,
                raiseOnOverflow: false,
                raiseOnUnderflow: false,
                raiseOnDivideByZero: false
            ))
        let roundedWeight = roundedDecimal.doubleValue
        
        // Drop trailing .0 for integers; keep one decimal otherwise
        let isInteger = abs(roundedWeight - roundedWeight.rounded()) < AppConstants.Precision.doubleEqualityEpsilon
        if isWeightlessMode {
            let prefix = roundedWeight > 0 ? "+" : "" // minus handled by formatting
            if isInteger {
                return "\(prefix)\(Int(roundedWeight.rounded()))"
            } else {
                return String(format: "%@%.1f", prefix, roundedWeight)
            }
        } else {
            if isInteger {
                return "\(Int(roundedWeight.rounded()))"
            } else {
                return String(format: "%.1f", roundedWeight)
            }
        }
    }

    // MARK: - Goal State Management
    func resetGoalState() {
        state = GoalState()
    }

    func updateGoalType(_ type: GoalType) {
        state.goalType = type
    }

    func updateGoalUnit(_ unit: WeightUnit) {
        state.goalUnit = unit
    }
    func convertStoredWeightToDisplay(_ storedWeight: Int) -> Double {
        let unit = accountService.activeAccount?.weightSettings?.weightUnit ?? .lb
        if unit == .kg {
            return ConversionTools.convertStoredToKg(storedWeight)
        } else {
            return ConversionTools.convertStoredToLbs(storedWeight)
        }
    }

    // MARK: - Weight Formatting Methods (moved from DashboardStore)
    
    /// Returns the current weight unit as a string (e.g., "lbs" or "kg")
    func getUnitText() -> String {
        return accountService.activeAccount?.weightSettings?.weightUnit?.rawValue ?? "lbs"
    }

    /// Returns the weight display label for the current period
    func getWeightDisplayLabel(for period: TimePeriod) -> String {
        return "\(period.rawValue) average"
    }

    /// Returns true if there are entries but none in the current time period
    func hasEntriesButNoneInCurrentPeriod(continuousOperations: [BathScaleWeightSummary], visibleOperations: [BathScaleWeightSummary]) -> Bool {
        return !continuousOperations.isEmpty && visibleOperations.isEmpty
    }

    // Updates visible data after scroll ends (forces UI update and logs average weight)
    // swiftlint:disable:next function_parameter_count
    func updateVisibleDataAfterScroll(visibleOperations: [BathScaleWeightSummary], isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double, triggerUpdate: @escaping () -> Void, logAverage: @escaping (Double) -> Void) {
        triggerUpdate()
        let opsToUse = visibleOperations.isEmpty ? visibleOperations : visibleOperations
        let weightValues = opsToUse.map { summary -> Double in
            if isWeightlessMode {
                guard let anchorWeight = anchorWeight else { return 0 }
                let currentWeight = convertWeight(Int(summary.weight))
                return currentWeight - anchorWeight
            } else {
                return convertWeight(Int(summary.weight))
            }
        }
        if !weightValues.isEmpty {
            let rawAverage = weightValues.reduce(0, +) / Double(weightValues.count)
            // Apply same robust rounding logic as other weight calculations
            let roundedAverage = (rawAverage * 100).rounded(.toNearestOrAwayFromZero) / 100
            logAverage(roundedAverage)
        }
    }
}
