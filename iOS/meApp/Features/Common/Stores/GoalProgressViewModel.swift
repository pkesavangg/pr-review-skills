import Foundation
import SwiftUI
import Combine

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
    @Injector private var accountService: AccountService
    @Injector private var entryService: EntryService
    
    // MARK: - Private
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: - Init
    init() {
        // Recalculate whenever active account changes.
        accountService.$activeAccount
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
    private func loadData() async {
        isLoaded = false
        guard let account = accountService.activeAccount else { return }
        guard let goalSettings = account.goalSettings else {
            // No goal configured
            goalType = .none
            delta = 0
            startWeight = 0
            goalWeight = 0
            progress = 0
            weightlessOn = account.weightlessSettings?.isWeightlessOn ?? false
            let weightUnit = account.weightSettings?.weightUnit ?? .lb
            unit = weightUnit.rawValue
            isLoaded = true
            return
        }
        
        // Determine weight unit
        let weightUnit = account.weightSettings?.weightUnit ?? .lb
        unit = weightUnit.rawValue
        
        goalType = goalSettings.goalType ?? .none
        
        let initialWeightStored  = Int(goalSettings.initialWeight ?? 0)
        let goalWeightStored     = Int(goalSettings.goalWeight    ?? 0)
        
        // Current weight from latest entry (falls back to initial weight on error)
        var currentWeightStored: Int = initialWeightStored
        do {
            if let latest = try await entryService.getLatestEntry() {
                currentWeightStored = Int(latest.scaleEntry?.weight ?? 0)
            }
        } catch { /* ignore – keep fallback */ }
        
        // Weightless baseline (tenths-lbs) offset
        weightlessOn = account.weightlessSettings?.isWeightlessOn ?? false
        let baselineStored: Int = {
            guard weightlessOn,
                  let weight = account.weightlessSettings?.weightlessWeight else { return 0 }
            return Int(weight)
        }()
        
        // Convert stored (tenths-lb) values to display units, applying weightless offset.
        let initialDisplay  = convertStoredWeight(initialWeightStored - baselineStored,  unit: weightUnit)
        let goalDisplay     = convertStoredWeight(goalWeightStored    - baselineStored,  unit: weightUnit)
        let currentDisplay  = convertStoredWeight(currentWeightStored - baselineStored,  unit: weightUnit)
        
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
    }
    
    // MARK: - Helpers
    private func convertStoredWeight(_ stored: Int, unit: WeightUnit) -> Double {
        switch unit {
        case .kg: return ConversionTools.convertStoredToKg(stored)
        case .lb: return ConversionTools.convertStoredToLbs(stored)
        }
    }
}
