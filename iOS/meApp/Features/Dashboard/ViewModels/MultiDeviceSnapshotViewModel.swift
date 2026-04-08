import Combine
import Foundation

@MainActor
final class MultiDeviceSnapshotViewModel: ObservableObject {
    @Injector private var entryService: EntryService

    @Published private(set) var dailySummaries: [BathScaleWeightSummary] = []
    @Published private(set) var bpmDailySummaries: [BathScaleWeightSummary] = []
    @Published private(set) var babyDailySummaries: [String: [BathScaleWeightSummary]] = [:]

    private var cancellables = Set<AnyCancellable>()

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
    }

    func loadSnapshots(availableItems: [ProductSelection] = []) async {
        await entryService.loadDashboardData(entryType: .wg)
        await entryService.loadDashboardData(entryType: .bpm)

        // Load real baby data for available baby profiles
        for item in availableItems {
            if case .baby(let profile) = item {
                await entryService.loadBabyDashboardData(babyId: profile.id)
            }
        }
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

    private var shouldShowBpmSnapshot: Bool {
        !bpmDailySummaries.isEmpty
    }
}
