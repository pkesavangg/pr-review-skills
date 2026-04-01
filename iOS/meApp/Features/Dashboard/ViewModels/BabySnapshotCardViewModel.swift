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

    func convertStoredWeightToDisplay(_ storedWeight: Int) -> Double {
        let unit = activeAccount?.weightSettings?.weightUnit ?? .lb
        return unit == .kg
            ? ConversionTools.convertStoredToKg(storedWeight)
            : ConversionTools.convertStoredToLbs(storedWeight)
    }

    /// Converts a WHO percentile JSON value (decigrams) to the active display unit.
    func convertDecigramsToDisplay(_ decigrams: Int) -> Double {
        let unit = activeAccount?.weightSettings?.weightUnit ?? .lb
        // TODO: Remove this fallback once baby-scale conversion is confirmed and
        // implemented separately per SKU type.
        let kg = Double(decigrams) / BabyPercentileGrowthReference.decigramsToKgFactor
        let stored = ConversionTools.convertKgToStored(kg)
        return unit == .kg
            ? ConversionTools.convertStoredToKg(stored)
            : ConversionTools.convertStoredToLbs(stored)
    }

    func formatBabyWeight(_ storedWeight: Int) -> (lbs: String, oz: String) {
        let displayWeight = convertStoredWeightToDisplay(storedWeight)
        let wholeLbs = Int(displayWeight)
        let remainingOz = (displayWeight - Double(wholeLbs)) * 16.0
        return (lbs: "\(wholeLbs)", oz: String(format: "%.1f", remainingOz))
    }

    func weekAverageLbsOz(from summaries: [BathScaleWeightSummary]) -> (lbs: String, oz: String)? {
        let weights = summaries.map(\.weight).filter { $0 > 0 }
        guard !weights.isEmpty else { return nil }
        let avgStored = Int((weights.reduce(0, +) / Double(weights.count)).rounded())
        return formatBabyWeight(avgStored)
    }
}
