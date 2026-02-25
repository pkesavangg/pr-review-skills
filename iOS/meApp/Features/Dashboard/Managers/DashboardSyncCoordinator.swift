import Foundation

/// Coordinator implementation for managing dashboard sync, save,
/// and configuration loading operations.
@MainActor
final class DashboardSyncCoordinator: DashboardSyncCoordinatorProtocol {
    
    // MARK: - Dependencies
    
    @Injector private var entryService: EntryService
    @Injector private var logger: LoggerService
    @Injector private var accountService: AccountService
    @Injector private var notificationService: NotificationHelperService
    
    // MARK: - Constants
    
    private static let allProgressMetricsRemovedKey = "dashboard.allProgressMetricsRemoved"
    
    // MARK: - Sync Operations
    
    func syncEntries() async {
        await entryService.syncAllEntriesWithRemote()
    }
    
    // MARK: - Save Operations
    
    func saveChanges(
        saveMetrics: @escaping () async throws -> Void,
        saveProgressMetrics: @escaping () async throws -> Void,
        loadProgressMetrics: @escaping () async -> Void,
        onSuccess: @escaping () -> Void,
        onError: @escaping (Error) -> Void
    ) {
        notificationService.showLoader(LoaderModel(text: LoaderStrings.saving))
        Task {
            defer { notificationService.dismissLoader() }
            do {
                // Save dashboard metrics first
                try await saveMetrics()
                
                // Save progress metrics (goal card and streak items)
                try await saveProgressMetrics()
                
                // Reload progress metrics from already-updated account to sync UI state.
                // This ensures that streaks added back in edit mode are properly reflected when exiting edit mode.
                await loadProgressMetrics()
                
                onSuccess()
                logger.log(level: .success, tag: "DashboardSyncCoordinator", message: "Dashboard changes saved successfully")
            } catch {
                logger.log(level: .error, tag: "DashboardSyncCoordinator", message: "Failed to save dashboard changes: \(error)")
                onError(error)
            }
        }
    }
    
    func saveProgressMetricsToAPI(
        streakItems: [MetricItem],
        streakOrder: [String],
        goalCardPosition: Int,
        isGoalCardRemoved: Bool,
        removedStreaks: Set<String>,
        updateProgressMetrics: ([String]) async throws -> Void
    ) async throws {
        guard accountService.activeAccount != nil else {
            throw DashboardError.noActiveAccount
        }
        
        // Helper to map streak label to API value
        func mapStreakLabelToAPI(_ label: String) -> String? {
            if label == DashboardStrings.currentStreak {
                return "currentStreak"
            } else if label == DashboardStrings.longestStreak {
                return "longestStreak"
            } else if label.contains("/week") {
                return "weeklyChange"
            } else if label.contains("/month") {
                return "monthlyChange"
            } else if label.contains("/year") {
                return "yearlyChange"
            } else if label.contains("/total") {
                return "totalChange"
            }
            return nil
        }
        
        // Reconstruct ordered streaks from saved order
        var orderedStreaks: [MetricItem] = []
        if !streakOrder.isEmpty {
            // Map IDs to actual streak items preserving order
            orderedStreaks = streakOrder.compactMap { id in
                streakItems.first { $0.id.uuidString == id }
            }
            // Add any streaks not in the order list (new streaks)
            let missingStreaks = streakItems.filter { item in
                !streakOrder.contains(item.id.uuidString)
            }
            orderedStreaks.append(contentsOf: missingStreaks)
        } else {
            // No saved order, use default order
            orderedStreaks = streakItems
        }
        
        // Filter out removed streaks
        let activeStreaks = orderedStreaks.filter { !removedStreaks.contains($0.label) }
        
        // Build combined order: goal card + streaks
        // The goalCardPosition is the index in the combined grid (goal + all streaks)
        var progressMetrics: [String] = []
        
        if !isGoalCardRemoved {
            // Build combined array: insert goal at goalCardPosition
            var combinedItems: [String] = []
            
            // Add streaks first
            for streak in activeStreaks {
                if let apiValue = mapStreakLabelToAPI(streak.label) {
                    combinedItems.append(apiValue)
                }
            }
            
            // Insert goal at goalCardPosition (clamped to valid range)
            let clampedPosition = min(goalCardPosition, combinedItems.count)
            combinedItems.insert("goal", at: clampedPosition)
            
            progressMetrics = combinedItems
        } else {
            // Goal is removed, just add all active streaks
            for streak in activeStreaks {
                if let apiValue = mapStreakLabelToAPI(streak.label) {
                    progressMetrics.append(apiValue)
                }
            }
        }
        
        // Validate: only allow allowed values
        let allowedValues: Set<String> = ["goal", "currentStreak", "longestStreak", "weeklyChange", "monthlyChange", "yearlyChange", "totalChange"]
        progressMetrics = progressMetrics.filter { allowedValues.contains($0) }
        
        // Preserve saved streak order in edit mode; otherwise use default order
        let allMetricsRemoved = progressMetrics.isEmpty
        UserDefaults.standard.set(allMetricsRemoved, forKey: Self.allProgressMetricsRemovedKey)
        
        // Log the order being saved for debugging
        logger.log(level: .info, tag: "DashboardSyncCoordinator", message: "Saving progress metrics to API with order: \(progressMetrics), allRemoved: \(allMetricsRemoved)")
        
        // Save to API
        try await updateProgressMetrics(progressMetrics)
    }
    
    // MARK: - Configuration Loading
    
    func loadDashboardConfigurationFromAPI(config: DashboardConfigurationLoadConfig) async {
        do {
            // Refresh account data from API to ensure we have latest dashboard settings
            _ = try? await config.refreshAccount()
            
            // Sync entries before loading metrics to ensure we have latest entry data from all devices
            // This ensures metric values (like BMI) are consistent across devices
            // Note: This may be a no-op if already synced in initializeDashboard
            await config.syncEntries()
            
            // Load dashboard metrics configuration from API
            try await config.loadMetricsFromAPI()
            
            // Call onMetricsLoaded callback
            await MainActor.run {
                config.onMetricsLoaded()
            }
            
            // Load progress metrics (goal card + streaks) only if not already loaded
            // This prevents duplicate loading when called from initializeDashboard
            let progressMetricsAlreadyLoaded = await MainActor.run {
                // This check should be done by the caller
                false // Placeholder - caller should check this
            }
            
            if !progressMetricsAlreadyLoaded {
                // Refresh streak data with real values from API
                try await config.refreshStreakData()
                
                // Load progress metrics configuration from API
                await config.loadProgressMetrics()
                
                // Load goal card data to ensure it's ready before showing
                try? await config.loadGoalData()
                
                // Call onProgressMetricsLoaded callback
                await MainActor.run {
                    config.onProgressMetricsLoaded()
                }
            }
            
        } catch {
            config.onError(error)
            logger.log(level: .error, tag: "DashboardSyncCoordinator", message: "Failed to load dashboard configuration from API: \(error)")
        }
    }
    
    func loadProgressMetricsFromAccount(
        activeAccount: Account?,
        allStreaks: [MetricItem],
        streakManagerActiveCount: inout Int,
        onProgressMetricsLoaded: (Int, Bool, [String], Set<String>) -> Void,
        setupDefaultOrder: () -> Void
    ) async {
        guard let account = activeAccount else {
            await MainActor.run { setupDefaultOrder() }
            return
        }
        
        guard let progressMetricsString = account.dashboardSettings?.progressMetrics else {
            await MainActor.run { setupDefaultOrder() }
            return
        }
        
        let progressMetrics = progressMetricsString.isEmpty
            ? []
            : progressMetricsString.split(separator: ",").map { String($0) }.filter { !$0.isEmpty }
        
        // Handle case where API defaults empty progress metrics back to all metrics
        let allMetricsRemovedFlag = UserDefaults.standard.bool(forKey: Self.allProgressMetricsRemovedKey)
        let defaultMetricsList: Set<String> = ["goal", "currentStreak", "longestStreak", "weeklyChange", "monthlyChange", "yearlyChange", "totalChange"]
        let isDefaultFullList = Set(progressMetrics) == defaultMetricsList && progressMetrics.count == defaultMetricsList.count
        
        // If the flag is set and API returns the default full list, treat all as removed
        let shouldTreatAsAllRemoved = allMetricsRemovedFlag && (progressMetrics.isEmpty || isDefaultFullList)
        
        await MainActor.run {
            // Don't mark all as removed on initial load (empty API before user config)
            // This check should be done by the caller
            let isInitialLoad = false // Placeholder - caller should check this
            
            if progressMetrics.isEmpty && isInitialLoad {
                setupDefaultOrder()
                return
            }
            
            if (progressMetrics.isEmpty || shouldTreatAsAllRemoved) && !isInitialLoad {
                // All progress metrics are removed
                let goalCardPosition = 0
                let isGoalCardRemoved = true
                var streakGridOrder: [String] = []
                // Preserve saved streak order in edit mode; otherwise use default order
                // This should be handled by the caller
                let removedStreaks = Set(allStreaks.map { $0.label })
                
                streakManagerActiveCount = 0
                
                onProgressMetricsLoaded(goalCardPosition, isGoalCardRemoved, streakGridOrder, removedStreaks)
                return
            }
            
            guard !allStreaks.isEmpty else {
                setupDefaultOrder()
                return
            }
            
            var goalCardPosition: Int?
            var orderedStreakIds: [String] = []
            var foundStreakLabels: Set<String> = []
            
            for (index, apiValue) in progressMetrics.enumerated() {
                if apiValue == "goal" {
                    goalCardPosition = index
                } else if let streakLabel = mapAPIValueToStreakLabel(apiValue, allStreaks: allStreaks),
                          let streakItem = allStreaks.first(where: { $0.label == streakLabel }) {
                    orderedStreakIds.append(streakItem.id.uuidString)
                    foundStreakLabels.insert(streakLabel)
                }
            }
            
            let finalGoalCardPosition = goalCardPosition ?? 0
            let isGoalCardRemoved = goalCardPosition == nil
            let removedStreaks = Set(allStreaks.map { $0.label }).subtracting(foundStreakLabels)
            
            // Sync active streak count from UI removal state
            let activeCount = max(0, allStreaks.count - removedStreaks.count)
            streakManagerActiveCount = min(activeCount, allStreaks.count)
            
            onProgressMetricsLoaded(finalGoalCardPosition, isGoalCardRemoved, orderedStreakIds, removedStreaks)
        }
    }
    
    func loadMetricsFromLocalAccount(
        activeAccount: Account?,
        updateDashboardType: (DashboardType) -> Void,
        updateMetricsOrder: ([String]) -> Void,
        setupInitialMetrics: () -> Void,
        onMetricsLoaded: () -> Void
    ) async {
        await MainActor.run {
            // Try to load from local account if available
            if let account = activeAccount {
                let dashboardTypeString = account.dashboardSettings?.dashboardType
                let dashboardType: DashboardType
                switch dashboardTypeString {
                case "dashboard4":
                    dashboardType = .dashboard4
                case "dashboard12":
                    dashboardType = .dashboard12
                default:
                    dashboardType = .dashboard12
                }
                updateDashboardType(dashboardType)
                
                // Load metrics order from local account if available
                if let dashboardMetrics = account.dashboardSettings?.dashboardMetrics {
                    let metricArray = dashboardMetrics.split(separator: ",").map(String.init)
                    updateMetricsOrder(metricArray)
                } else {
                    setupInitialMetrics()
                }
            } else {
                // Set up default metrics if no account
                setupInitialMetrics()
            }
            
            onMetricsLoaded()
        }
    }
    
    func reloadDashboardConfiguration(
        fullRefresh: Bool,
        updateMetrics: Bool,
        loadConfiguration: () async -> Void,
        updateMetricsForView: () -> Void,
        scheduleUIUpdate: () -> Void,
        refreshDashboardState: () -> Void
    ) async {
        await loadConfiguration()
        if updateMetrics {
            updateMetricsForView()
        }
        await MainActor.run {
            scheduleUIUpdate()
            if fullRefresh {
                refreshDashboardState()
            }
        }
    }
    
    func refreshAll(
        syncEntries: () async -> Void,
        onAppearActions: () -> Void
    ) async {
        await syncEntries()
        onAppearActions()
    }
    
    // MARK: - API Mapping Helpers
    
    func mapAPIValueToStreakLabel(_ apiValue: String, allStreaks: [MetricItem]) -> String? {
        switch apiValue {
        case "currentStreak":
            return DashboardStrings.currentStreak
        case "longestStreak":
            return DashboardStrings.longestStreak
        case "weeklyChange":
            return allStreaks.first { $0.label.contains("/week") }?.label
        case "monthlyChange":
            return allStreaks.first { $0.label.contains("/month") }?.label
        case "yearlyChange":
            return allStreaks.first { $0.label.contains("/year") }?.label
        case "totalChange":
            return allStreaks.first { $0.label.contains("/total") }?.label
        default:
            return nil
        }
    }
    
    func mapStreakLabelToAPI(_ label: String) -> String? {
        if label == DashboardStrings.currentStreak {
            return "currentStreak"
        } else if label == DashboardStrings.longestStreak {
            return "longestStreak"
        } else if label.contains("/week") {
            return "weeklyChange"
        } else if label.contains("/month") {
            return "monthlyChange"
        } else if label.contains("/year") {
            return "yearlyChange"
        } else if label.contains("/total") {
            return "totalChange"
        }
        return nil
    }
}
