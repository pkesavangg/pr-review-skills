import Combine
import Foundation

@MainActor
final class WeightSnapshotCardViewModel: ObservableObject {
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

    func goalWeightForDisplay() -> Double? {
        guard let storedGoal = activeAccount?.goalSettings?.goalWeight else { return nil }
        return convertStoredWeightToDisplay(Int(storedGoal))
    }
}
