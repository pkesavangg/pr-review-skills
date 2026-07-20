import Foundation
import SwiftUI

/// Manages graph period transitions and chart-ready sequencing.
///
/// Responsibilities:
/// - Debounces chart data regeneration during rapid period changes.
/// - Coordinates the "graph ready" flag so the view fades in after data settles.
/// - Fires `onPeriodDidChange` so `DashboardGraphManager` can trigger dependent updates.
@MainActor
final class GraphAnimationManager {

    // MARK: - Period Change

    /// Called by `DashboardGraphManager` when the user selects a new time period.
    /// Fires the provided closure after a brief delay so chart data can be computed first.
    ///
    /// - Parameters:
    ///   - period: The newly-selected period.
    ///   - delay: Seconds to wait before marking the graph as ready (default: 0.15).
    ///   - onReady: Closure run on main actor when the transition is complete.
    func schedulePeriodTransition(
        for period: TimePeriod,
        delay: TimeInterval = 0.15,
        onReady: @escaping @MainActor () -> Void
    ) {
        cancelPendingTransition()
        periodTransitionTask = Task { [weak self] in
            guard self != nil else { return }
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            guard !Task.isCancelled else { return }
            await MainActor.run { onReady() }
        }
    }

    func cancelPendingTransition() {
        periodTransitionTask?.cancel()
        periodTransitionTask = nil
    }

    // MARK: - Chart Data Throttle

    /// Throttles chart data regeneration calls during rapid scrolling.
    ///
    /// - Parameters:
    ///   - interval: Minimum seconds between regeneration calls (default: 0.1).
    ///   - action: The regeneration work to run after the interval.
    func throttleChartDataGeneration(
        interval: TimeInterval = 0.1,
        action: @escaping @MainActor () -> Void
    ) {
        chartDataThrottleTimer?.invalidate()
        chartDataThrottleTimer = Timer.scheduledTimer(withTimeInterval: interval, repeats: false) { [weak self] _ in
            guard self != nil else { return }
            Task { @MainActor in action() }
        }
    }

    func cancelChartDataThrottle() {
        chartDataThrottleTimer?.invalidate()
        chartDataThrottleTimer = nil
    }

    // MARK: - Private

    // `nonisolated(unsafe)` lets deinit cancel these without hopping to the main actor.
    nonisolated(unsafe) private var periodTransitionTask: Task<Void, Never>?
    nonisolated(unsafe) private var chartDataThrottleTimer: Timer?

    deinit {
        periodTransitionTask?.cancel()
        chartDataThrottleTimer?.invalidate()
    }
}
