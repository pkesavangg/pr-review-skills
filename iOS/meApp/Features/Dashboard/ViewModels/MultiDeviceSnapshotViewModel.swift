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

    func loadSnapshots() async {
        await entryService.loadDashboardData(entryType: .wg)
        await entryService.loadDashboardData(entryType: .bpm)
    }

    /// Filters available items to show only one baby snapshot (the latest added / last in list).
    func snapshotItems(from availableItems: [ProductSelection]) -> [ProductSelection] {
        var items: [ProductSelection] = []
        var latestBaby: ProductSelection?
        for item in availableItems {
            if case .baby = item {
                latestBaby = item
            } else {
                items.append(item)
            }
        }
        if let baby = latestBaby {
            items.append(baby)
        }
        return items
    }

    func babySummaries(for babyId: String) -> [BathScaleWeightSummary] {
        let real = babyDailySummaries[babyId] ?? []
        // TODO: Remove dummy data once baby entry pipeline is wired
        return real.isEmpty ? Self.makeDummyBabySummaries() : real
    }

    // MARK: - Dummy Data

    /// Temporary sample data for baby snapshot card UI development.
    /// Remove once real baby weight entries flow through EntryService.
    private static func makeDummyBabySummaries() -> [BathScaleWeightSummary] {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let storedWeights: [Double] = [1420, 1440, 1475, 1500, 1530, 1560, 1600]
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"

        return storedWeights.enumerated().compactMap { index, weight in
            guard let date = calendar.date(byAdding: .day, value: index - 6, to: today) else { return nil }
            return BathScaleWeightSummary(
                accountId: "dummy",
                period: formatter.string(from: date),
                entryTimestamp: date.ISO8601Format(),
                date: date,
                count: 1,
                weight: weight
            )
        }
    }
}
