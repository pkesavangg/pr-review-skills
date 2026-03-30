import Combine
import Foundation

@MainActor
final class MultiDeviceSnapshotViewModel: ObservableObject {
    @Injector private var entryService: EntryService

    @Published private(set) var dailySummaries: [BathScaleWeightSummary] = []
    @Published private(set) var bpmDailySummaries: [BathScaleWeightSummary] = []

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
}
