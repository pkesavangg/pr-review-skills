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
    /// MOB-1726: mirrors `EntryService.hasCompletedInitialSync` so the view can keep showing skeletons
    /// (rather than the empty state) until the first login sync lands the account's real entries.
    @Published private(set) var hasCompletedInitialSync: Bool = false

    private var cancellables = Set<AnyCancellable>()
    private var lastLoadedSignature: Int?
    private var activeLoadSignature: Int?

    init() {
        dailySummaries = entryService.dailySummaries
        bpmDailySummaries = entryService.bpmDailySummaries
        hasCompletedInitialSync = entryService.hasCompletedInitialSync

        entryService.$hasCompletedInitialSync
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in self?.hasCompletedInitialSync = $0 }
            .store(in: &cancellables)

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
                // MOB-1726: don't latch a CANCELLED load as complete. The dashboard's persistence
                // redirect can tear this view down (cancelling the task) before the baby fetch publishes,
                // leaving the baby card empty while the weight card — a faster local-DB aggregate that
                // already finished — shows data. Recording `lastLoadedSignature` here would make the guard
                // above early-return forever; skipping it lets the remounted overview reload. A genuinely
                // empty baby (no entries) still completes without cancellation, so it latches normally and
                // never retries in a loop.
                if !Task.isCancelled {
                    lastLoadedSignature = signature
                }
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

    /// MOB-1726: whether to show the skeleton (vs the real card) for a snapshot item.
    /// Skeletons show while the first snapshot load for this set is still running, AND — the fix —
    /// afterwards while the item still has no data and the initial remote sync hasn't finished, so a fresh
    /// login shows skeletons instead of flashing the empty "no entries" card before entries land. Once the
    /// item has data, or the initial sync is done (empty is now genuine), the real card shows. A pending-baby
    /// placeholder has no data to load, so it's never a skeleton.
    func shouldShowSkeleton(for item: ProductSelection, in snapshotItems: [ProductSelection]) -> Bool {
        if case .baby(let profile) = item, profile.isPendingSelection { return false }
        let loaded = isSnapshotReady(item) || hasLoadedSnapshots(for: snapshotItems)
        return !loaded ? true : (!hasData(for: item) && !hasCompletedInitialSync)
    }

    /// MOB-1726: reads `entryService` DIRECTLY, not the `@Published` mirrors on this VM. The mirrors update
    /// via `.receive(on: .main)` (an async hop), so when the initial sync ends they can lag `hasCompletedInitialSync`
    /// by a runloop turn — a one-frame window where the flag is set but the mirrored summaries are still empty,
    /// which flashed the empty card before the data landed. The service always sets the reloaded summaries
    /// before the flag, so a direct read is populated the moment the flag is observed. (The mirror
    /// subscriptions still drive the re-render; only this decision reads the authoritative value.)
    private func hasData(for item: ProductSelection) -> Bool {
        switch item {
        case .myWeight:
            return !entryService.dailySummaries.isEmpty
        case .myBloodPressure:
            return !entryService.bpmDailySummaries.isEmpty
        case .baby(let profile):
            return !babySummaries(for: profile).isEmpty
        }
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
