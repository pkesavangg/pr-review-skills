import Combine
import Foundation

@MainActor
final class MultiDeviceSnapshotViewModel: ObservableObject {
    @Injector private var entryService: EntryService

    @Published private(set) var dailySummaries: [BathScaleWeightSummary] = []
    @Published private(set) var bpmDailySummaries: [BathScaleWeightSummary] = []
    @Published private(set) var babyDailySummaries: [String: [BathScaleWeightSummary]] = [:]
    @Published private(set) var isLoadingSnapshots: Bool = false
    @Published private(set) var readySnapshotIDs: Set<String> = []

    private var cancellables = Set<AnyCancellable>()
    private var lastLoadedSignature: Int?
    private var activeLoadSignature: Int?

    init() {
        dailySummaries = entryService.dailySummaries
        bpmDailySummaries = entryService.bpmDailySummaries

        entryService.$dailySummaries
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in self?.dailySummaries = $0 }
            .store(in: &cancellables)

        entryService.$bpmDailySummaries
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in self?.bpmDailySummaries = $0 }
            .store(in: &cancellables)

        entryService.$babyDailySummariesByProfile
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in self?.babyDailySummaries = $0 }
            .store(in: &cancellables)
    }

    func loadSnapshots(availableItems: [ProductSelection] = []) async {
        let signature = snapshotLoadSignature(for: availableItems)
        if activeLoadSignature == signature || lastLoadedSignature == signature {
            return
        }
        activeLoadSignature = signature
        isLoadingSnapshots = true
        readySnapshotIDs = []

        defer {
            if activeLoadSignature == signature {
                lastLoadedSignature = signature
                activeLoadSignature = nil
                isLoadingSnapshots = false
            }
        }

        let babyProfiles = availableItems.compactMap { item -> BabyProfile? in
            guard case .baby(let profile) = item else { return nil }
            guard !profile.isPendingSelection else { return nil }
            return profile
        }
        let shouldLoadWeight = availableItems.contains(.myWeight)
        let shouldLoadBpm = availableItems.contains(.myBloodPressure)

        await withTaskGroup(of: String?.self) { group in
            if shouldLoadWeight {
                group.addTask { [entryService] in
                    await entryService.loadDashboardData(entryType: .scale)
                    return "weight"
                }
            }
            if shouldLoadBpm {
                group.addTask { [entryService] in
                    await entryService.loadDashboardData(entryType: .bpm)
                    return "bpm"
                }
            }

            for profile in babyProfiles {
                group.addTask { [entryService] in
                    await entryService.loadBabyDashboardData(babyId: profile.id)
                    return "baby-\(profile.id)"
                }
            }

            for await snapshotID in group {
                guard activeLoadSignature == signature, let snapshotID else { continue }
                readySnapshotIDs.insert(snapshotID)
            }
        }
    }

    private func snapshotLoadSignature(for availableItems: [ProductSelection]) -> Int {
        var hasher = Hasher()
        for item in availableItems {
            switch item {
            case .myWeight:
                hasher.combine("weight")
            case .myBloodPressure:
                hasher.combine("bpm")
            case .baby(let profile):
                if profile.isPendingSelection {
                    hasher.combine("baby-pending")
                    break
                }
                hasher.combine("baby")
                hasher.combine(profile.id)
            }
        }
        return hasher.finalize()
    }

    /// Returns the items to display on the multi-device snapshot overview.
    /// Non-baby items (weight, BPM) are always included.
    /// All baby items are collapsed to a single card:
    ///   • The currently selected baby when a baby is selected (last-active — persisted across sessions).
    ///   • The first baby in the list when no baby is currently selected (e.g. user is on weight view).
    ///   • The pending placeholder when a baby scale is paired but no baby profile has been added yet.
    /// This implements MOB-435: one baby snapshot, driven by the last-active baby.
    func snapshotItems(from availableItems: [ProductSelection], selectedItem: ProductSelection) -> [ProductSelection] {
        var nonBabyItems: [ProductSelection] = []
        var babyItems: [ProductSelection] = []
        var pendingBabyItem: ProductSelection?

        var hasBpmItem = false
        for item in availableItems {
            if case .baby(let profile) = item {
                if profile.isPendingSelection {
                    pendingBabyItem = item
                } else {
                    babyItems.append(item)
                }
            } else {
                nonBabyItems.append(item)
                if case .myBloodPressure = item {
                    hasBpmItem = true
                }
            }
        }

        // Surface a BPM snapshot when BPM data exists but BPM isn't already in the list.
        if !hasBpmItem && shouldShowBpmSnapshot {
            nonBabyItems.append(.myBloodPressure)
        }

        // Prefer the currently selected baby; fall back to the first baby in the list.
        let activeBaby: ProductSelection? = {
            if case .baby = selectedItem, babyItems.contains(selectedItem) {
                return selectedItem
            }
            return babyItems.first
        }()

        if let baby = activeBaby {
            return nonBabyItems + [baby]
        }
        // No real babies — show NoBabySnapshotCard when baby scale is paired but no profile added
        if let pending = pendingBabyItem {
            return nonBabyItems + [pending]
        }
        return nonBabyItems
    }

    private var shouldShowBpmSnapshot: Bool {
        !bpmDailySummaries.isEmpty
    }

    func babySummaries(for babyProfile: BabyProfile) -> [BathScaleWeightSummary] {
        if babyProfile.isPendingSelection {
            return []
        }
        // Real baby data only. When the baby has no weight entries we return empty so the
        // snapshot card renders its empty state (no value, no plotted curves) instead of
        // plotting synthetic dummy growth data — matching the full dashboard's empty grid.
        return entryService.babyDailySummariesByProfile[babyProfile.id]
            ?? babyDailySummaries[babyProfile.id]
            ?? []
    }

    /// Returns true when the current snapshot set has already completed at least one load.
    /// This lets the view show a skeleton before the first async load starts, avoiding
    /// an empty flash on initial render.
    func hasLoadedSnapshots(for availableItems: [ProductSelection]) -> Bool {
        lastLoadedSignature == snapshotLoadSignature(for: availableItems)
    }

    func isSnapshotReady(_ item: ProductSelection) -> Bool {
        readySnapshotIDs.contains(snapshotID(for: item))
    }

    private func snapshotID(for item: ProductSelection) -> String {
        switch item {
        case .myWeight:
            return "weight"
        case .myBloodPressure:
            return "bpm"
        case .baby(let profile):
            return "baby-\(profile.id)"
        }
    }
}
