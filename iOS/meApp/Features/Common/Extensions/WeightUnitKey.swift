import SwiftUI

/// Provides the user-selected weight unit (kg / lb) throughout the SwiftUI hierarchy via `@Environment(\.weightUnit)`.

private struct WeightUnitKey: EnvironmentKey {
    static let defaultValue: WeightUnit = .kg
}

private struct WeightlessSettingsKey: EnvironmentKey {
    static let defaultValue: WeightlessSettings? = nil
}

extension EnvironmentValues {
    /// Current weight unit selected in the account's body settings.
    var weightUnit: WeightUnit {
        get { self[WeightUnitKey.self] }
        set { self[WeightUnitKey.self] = newValue }
    }

    /// Current weight-less mode settings for the active account (nil if disabled / not set).
    var weightlessSettings: WeightlessSettings? {
        get { self[WeightlessSettingsKey.self] }
        set { self[WeightlessSettingsKey.self] = newValue }
    }
}

/// Injects `weightUnit` into the environment, updating automatically when the `AccountService` changes.
struct WeightUnitModifier: ViewModifier {
    @EnvironmentObject private var accountService: AccountService

    func body(content: Content) -> some View {
        let account = accountService.activeAccount
        let unit = account?.weightUnit ?? .kg
        let weightless: WeightlessSettings? = {
            guard let account else { return nil }
            return WeightlessSettings(
                accountId: account.accountId,
                isWeightlessOn: account.isWeightlessOn,
                weightlessTimestamp: account.weightlessTimestamp,
                weightlessWeight: account.weightlessWeight
            )
        }()

        content
            .environment(\.weightUnit, unit)
            .environment(\.weightlessSettings, weightless)
    }
}

extension View {
    /// Call this once near the root of the hierarchy so descendants can use `@Environment(\.weightUnit)`.
    func weightUnitable() -> some View {
        modifier(WeightUnitModifier())
    }
}
