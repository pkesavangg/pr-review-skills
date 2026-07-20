import SwiftUI

/// ViewModifier that handles scroll detection using iOS 18+ onScrollPhaseChange
struct ScrollDetectionModifier: ViewModifier {
    let dashboardStore: DashboardStore
    @Binding var selectedXValue: Date?

    func body(content: Content) -> some View {
        content
            .onScrollPhaseChange { _, newPhase in
                Task { @MainActor in
                    await dashboardStore.chartManager.handleScrollPhaseChange(to: newPhase)

                    // Immediately clear local selection state when scrolling starts
                    if newPhase == .interacting {
                        selectedXValue = nil
                    }
                }
            }
    }
}
