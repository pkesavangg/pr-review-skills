import SwiftUI
/// ViewModifier that handles scroll detection using iOS 18+ onScrollPhaseChange when available,
/// with fallback to simultaneousGesture for older iOS versions
struct ScrollDetectionModifier: ViewModifier {
    let dashboardStore: DashboardStore
    @Binding var hasDetectedScrollInCurrentGesture: Bool
    @Binding var selectedXValue: Date?

    func body(content: Content) -> some View {
        if #available(iOS 18.0, *) {
            content
                .onScrollPhaseChange { oldPhase, newPhase in
                    Task { @MainActor in
                        await dashboardStore.handleScrollPhaseChange(to: newPhase)

                        // Clear local selection state when scrolling starts
                        if newPhase == .interacting {
                            selectedXValue = nil
                        }
                    }
                }
        } else {
            content
                .simultaneousGesture(
                    DragGesture(minimumDistance: 3)
                        .onChanged { value in
                            let isHorizontalScroll = abs(value.translation.width) > abs(value.translation.height) * 1.5
                            let isSignificantMovement = abs(value.translation.width) > 8

                            if isHorizontalScroll && isSignificantMovement && !hasDetectedScrollInCurrentGesture {
                                hasDetectedScrollInCurrentGesture = true
                                dashboardStore.handleScrollStart()

                                // Clear local selection state when scrolling starts
                                selectedXValue = nil
                            }
                        }
                        .onEnded { value in
                            hasDetectedScrollInCurrentGesture = false
                            dashboardStore.handleScrollEndOptimized()
                        }
                )
        }
    }
}
