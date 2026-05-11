import Combine
import Foundation
import SwiftUI

/// ViewModel responsible for supplying data for `GoalProgressView`.
/// It observes the `AccountService` for changes to the active account and
/// recalculates goal-related information whenever the account, goal, or weight
/// unit changes.
@MainActor
final class GoalProgressViewModel: ObservableObject {
    
    // MARK: - Published Properties
    @Published var delta: Double = 0               // Difference to goal (display units)
    @Published var startWeight: Double = 0         // Initial weight (display units)
    @Published var goalWeight: Double = 0          // Goal weight (display units)
    @Published var progress: CGFloat = 0           // 0…1 progress towards goal
    @Published var goalType: GoalType = .none      // Current goal type
    @Published var unit: String = WeightUnit.lb.rawValue // "lb" | "kg"
    @Published var weightlessOn: Bool = false           // Weightless mode flag
    @Published var isLoaded: Bool = false               // Prevents transient UI during async load
    
    // MARK: - Dependencies
    @Injector private var accountService: AccountServiceProtocol
    @Injector private var entryService: EntryServiceProtocol
    
    // MARK: - Private
    private var cancellables = Set<AnyCancellable>()
    private var hasLoadedOnce = false
    
    // MARK: - Init
    init() {
        // Recalculate whenever active account changes.
        accountService.activeAccountPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                Task { await self?.loadData() }
            }
            .store(in: &cancellables)
        
        entryService.entrySaved
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                Task { await self?.loadData() }
            }
            .store(in: &cancellables)
        
        // Initial load
        Task { await loadData() }
    }
    
    // MARK: - Data Loading
// swiftlint:disable:next function_body_length
    private func loadData() async {
        // Keep last-rendered UI visible after initial load to avoid flicker.
        // Only gate the UI (isLoaded = false) before the very first load.
        if !hasLoadedOnce {
            isLoaded = false
        }
        guard let account = accountService.activeAccount else { return }
        guard account.goalType != nil else {
            // No goal configured
            goalType = .none
            delta = 0
            startWeight = 0
            goalWeight = 0
            progress = 0
            weightlessOn = account.isWeightlessOn
            let weightUnit = account.weightUnit
            unit = weightUnit.rawValue
            isLoaded = true
            return
        }

        // Extract all account data BEFORE any async operations
        let weightUnit = account.weightUnit
        let goalTypeValue = account.goalType ?? .none
        let initialWeightStored = Int(account.initialWeight ?? 0)
        let goalWeightStored = Int(account.goalWeight ?? 0)
        let isWeightlessOn = account.isWeightlessOn
        let weightlessWeight = account.weightlessWeight

        unit = weightUnit.rawValue
        goalType = goalTypeValue

        // Current weight from latest entry (falls back to initial weight on error)
        var currentWeightStored: Int = initialWeightStored
        do {
            if let latest = try await entryService.getLatestEntry() {
                // Extract Entry relationship data immediately
                currentWeightStored = Int(latest.scaleEntry?.weight ?? 0)
            }
        } catch { /* ignore – keep fallback */ }

        // Weightless baseline (tenths-lbs) offset
        weightlessOn = isWeightlessOn
        let baselineStored: Int = {
            guard isWeightlessOn,
                  let weight = weightlessWeight else { return 0 }
            return Int(weight)
        }()
        
        // Convert stored (tenths-lb) values to display units, applying weightless offset.
        let initialDisplay  = convertStoredWeight(initialWeightStored - baselineStored, unit: weightUnit)
        let goalDisplay     = convertStoredWeight(goalWeightStored - baselineStored, unit: weightUnit)
        let currentDisplay  = convertStoredWeight(currentWeightStored - baselineStored, unit: weightUnit)
        
        // Populate published properties
        startWeight = initialDisplay
        goalWeight  = goalDisplay
        delta       = goalDisplay - currentDisplay       // Negative when losing weight
        
        // Progress (0…1). For maintain goals we keep it at 0.
        if goalType == .maintain {
            progress = 0
        } else {
            let totalDistance = abs(goalDisplay - initialDisplay)
            // Determine direction (+1 for gain, -1 for lose)
            let direction: Double = (goalDisplay - initialDisplay) >= 0 ? 1 : -1
            // Signed distance moved towards the goal (negative means moving away).
            let signedAchieved   = (currentDisplay - initialDisplay) * direction
            let clampedAchieved  = max(signedAchieved, 0) // Prevent negative progress
            progress = totalDistance > 0 ? CGFloat(min(clampedAchieved / totalDistance, 1)) : 1
        }
        isLoaded = true
        hasLoadedOnce = true
    }
    
    // MARK: - Helpers
    private func convertStoredWeight(_ stored: Int, unit: WeightUnit) -> Double {
        switch unit {
        case .kg: return ConversionTools.convertStoredToKg(stored)
        case .lb: return ConversionTools.convertStoredToLbs(stored)
        }
    }
}
