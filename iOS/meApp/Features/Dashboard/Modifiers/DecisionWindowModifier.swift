import SwiftUI
/// ViewModifier that implements a decision window to determine touch interaction mode
struct DecisionWindowModifier: ViewModifier {
    @Binding var touchInteractionMode: TouchInteractionMode
    @Binding var initialTouchPoint: CGPoint
    @Binding var decisionTimer: Timer?
    @Binding var selectedXValue: Date?
    let dashboardStore: DashboardStore

    // Constants for decision logic
    private let decisionWindowDuration: TimeInterval = 0.15 // 150ms
    private let movementThreshold: CGFloat = 12 // 10-12 points
    private let scrollThreshold: CGFloat = 10 // 8-10 points
    private let horizontalScrollRatio: CGFloat = 1.5 // abs(dx) > 1.5 * abs(dy)

    func body(content: Content) -> some View {
        content
            .simultaneousGesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        handleTouchChange(value)
                    }
                    .onEnded { value in
                        handleTouchEnd(value)
                    }
            )
    }

    private func handleTouchChange(_ value: DragGesture.Value) {
        let translation = value.translation

        switch touchInteractionMode {
        case .none:
            // Start decision window
            touchInteractionMode = .deciding
            initialTouchPoint = value.location
            startDecisionTimer()

        case .deciding:
            // Check if we should enter scroll mode early
            let dx = abs(translation.width)
            let dy = abs(translation.height)
            let isHorizontalMovement = dx > horizontalScrollRatio * dy && dx > scrollThreshold

            if isHorizontalMovement {
                enterScrollMode()
            }
            // If not horizontal scrolling, let timer decide

        case .scrubbing:
            // Continue scrubbing - chartXSelection will handle this
            break

        case .scrolling:
            // Let existing scroll detection handle it
            break
        }
    }

    private func handleTouchEnd(_ value: DragGesture.Value) {
        cancelDecisionTimer()

        switch touchInteractionMode {
        case .scrubbing:
            // Clear selection when user lifts finger
            selectedXValue = nil

        case .scrolling:
            // Let existing scroll detection handle scroll end
            break

        case .deciding, .none:
            // For quick taps, let the chart's natural selection work
            break
        }

        // Reset interaction mode
        touchInteractionMode = .none
    }

    private func startDecisionTimer() {
        cancelDecisionTimer()

        decisionTimer = Timer.scheduledTimer(withTimeInterval: decisionWindowDuration, repeats: false) { _ in
            Task { @MainActor in
                // Timer fired - if still in deciding mode, enter scrub mode
                if touchInteractionMode == .deciding {
                    enterScrubMode()
                }
            }
        }
    }

    private func cancelDecisionTimer() {
        decisionTimer?.invalidate()
        decisionTimer = nil
    }

    private func enterScrubMode() {
        guard touchInteractionMode == .deciding else { return }

        touchInteractionMode = .scrubbing
        cancelDecisionTimer()
    }

    private func enterScrollMode() {
        guard touchInteractionMode == .deciding else { return }

        touchInteractionMode = .scrolling
        cancelDecisionTimer()

        // Clear active selection so crosshair disappears
        selectedXValue = nil
    }
}
