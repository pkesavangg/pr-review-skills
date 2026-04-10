//
//  BabySnapshotCardViewModel.swift
//  meApp
//
//  ViewModel for the baby snapshot card in the multi-device dashboard.
//  Provides weight unit conversion for baby measurements.
//

import Combine
import Foundation

@MainActor
final class BabySnapshotCardViewModel: ObservableObject {
    @Injector private var accountService: AccountServiceProtocol

    @Published private(set) var activeAccount: Account?

    private var cancellables = Set<AnyCancellable>()

    init() {
        activeAccount = accountService.activeAccount

        accountService.activeAccountPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in self?.activeAccount = $0 }
            .store(in: &cancellables)
    }

    var unitText: String {
        activeAccount?.weightSettings?.weightUnit?.rawValue ?? "lbs"
    }

    private var weightUnit: WeightUnit {
        activeAccount?.weightSettings?.weightUnit ?? .lb
    }

    func convertStoredWeightToDisplay(_ storedWeight: Int) -> Double {
        BabyDashboardChartSupport.convertStoredWeightToDisplay(storedWeight, unit: weightUnit)
    }

    func convertDecigramsToDisplay(_ decigrams: Int) -> Double {
        BabyDashboardChartSupport.convertDecigramsToDisplay(decigrams, unit: weightUnit)
    }

    func formatBabyWeight(_ storedWeight: Int) -> (lbs: String, oz: String) {
        BabyDashboardChartSupport.formatBabyWeight(storedWeight, unit: weightUnit)
    }

    func weekAverageLbsOz(from summaries: [BathScaleWeightSummary]) -> (lbs: String, oz: String)? {
        BabyDashboardChartSupport.weekAverageLbsOz(from: summaries, unit: weightUnit)
    }
}
