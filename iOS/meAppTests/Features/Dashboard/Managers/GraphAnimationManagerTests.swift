import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct GraphAnimationManagerTests {
    private func makeSUT() -> GraphAnimationManager {
        GraphAnimationManager()
    }

    @Test("schedulePeriodTransition: invokes onReady after the delay")
    func schedulePeriodTransitionRuns() async {
        let sut = makeSUT()
        var readyCount = 0
        sut.schedulePeriodTransition(for: .month, delay: 0.01) {
            readyCount += 1
        }

        var completed = false
        for _ in 0..<20 {
            try? await Task.sleep(nanoseconds: 10_000_000)
            await Task.yield()
            if readyCount == 1 {
                completed = true
                break
            }
        }

        #expect(completed)
        #expect(readyCount == 1)
    }

    @Test("schedulePeriodTransition: cancellation prevents the callback")
    func schedulePeriodTransitionCancellation() async {
        let sut = makeSUT()
        var readyCount = 0

        sut.schedulePeriodTransition(for: .week, delay: 0.05) {
            readyCount += 1
        }
        sut.cancelPendingTransition()

        try? await Task.sleep(nanoseconds: 80_000_000)
        #expect(readyCount == 0)
    }

    @Test("throttleChartDataGeneration: coalesces rapid calls into the latest action")
    func throttleChartDataGeneration() async {
        let sut = makeSUT()
        var calls: [String] = []

        sut.throttleChartDataGeneration(interval: 0.02) {
            calls.append("first")
        }
        sut.throttleChartDataGeneration(interval: 0.02) {
            calls.append("second")
        }

        try? await Task.sleep(nanoseconds: 80_000_000)
        await MainActor.run {
            RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        }
        #expect(calls == ["second"])
    }

    @Test("cancelChartDataThrottle: prevents the throttled action from firing")
    func cancelChartDataThrottle() async {
        let sut = makeSUT()
        var callCount = 0

        sut.throttleChartDataGeneration(interval: 0.05) {
            callCount += 1
        }
        sut.cancelChartDataThrottle()

        try? await Task.sleep(nanoseconds: 80_000_000)
        #expect(callCount == 0)
    }
}
