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
            return profile
        }

        await withTaskGroup(of: String?.self) { group in
            group.addTask { [entryService] in
                await entryService.loadDashboardData(entryType: .wg)
                return "weight"
            }
            group.addTask { [entryService] in
                await entryService.loadDashboardData(entryType: .bpm)
                return "bpm"
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
                hasher.combine("baby")
                hasher.combine(profile.id)
            }
        }
        return hasher.finalize()
    }

    /// Filters available items to show only one baby snapshot (the latest added / last in list).
    func snapshotItems(from availableItems: [ProductSelection]) -> [ProductSelection] {
        var items: [ProductSelection] = []
        var latestBaby: ProductSelection?
        var hasBpmSnapshot = false

        for item in availableItems {
            if case .baby = item {
                latestBaby = item
            } else {
                items.append(item)
                if case .myBloodPressure = item {
                    hasBpmSnapshot = true
                }
            }
        }

        if !hasBpmSnapshot && shouldShowBpmSnapshot {
            items.append(.myBloodPressure)
        }

        if let baby = latestBaby {
            items.append(baby)
        }
        return items
    }

    func babySummaries(for babyProfile: BabyProfile) -> [BathScaleWeightSummary] {
        // Use real baby data from EntryService if available, otherwise fall back to dummy data
        let real = entryService.babyDailySummariesByProfile[babyProfile.id]
            ?? babyDailySummaries[babyProfile.id]
            ?? []
        return real.isEmpty
            ? BabyDashboardChartSupport.dummyDailySummaries(for: babyProfile)
            : real
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

    private var shouldShowBpmSnapshot: Bool {
        !bpmDailySummaries.isEmpty
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
