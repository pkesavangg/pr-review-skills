import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct SetupLoaderViewModelTests {

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 3_000_000_000,
        pollNanoseconds: UInt64 = 50_000_000,
        condition: @escaping @MainActor () -> Bool
    ) async -> Bool {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while !condition() && ContinuousClock.now < deadline {
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
        return condition()
    }

    // MARK: - Initial State

    @Test("initialises with five resting dots and loading state")
    func initialState() {
        let sut = SetupLoaderViewModel()

        #expect(sut.dotScales.count == 5)
        #expect(sut.dotScales.allSatisfy { $0 == 0.5 })
        #expect(sut.connectionState == .loading)
    }

    // MARK: - startAnimation

    @Test("startAnimation animates the dot scales away from rest while loading")
    func startAnimationUpdatesDotScales() async {
        let sut = SetupLoaderViewModel()

        sut.startAnimation()

        let animated = await waitUntil { sut.dotScales.contains { $0 != 0.5 } }
        #expect(animated == true)

        sut.stopAnimation()
    }

    @Test("startAnimation is a no-op when the connection state is not loading")
    func startAnimationNoOpWhenNotLoading() {
        let sut = SetupLoaderViewModel()
        sut.connectionState = .success

        sut.startAnimation()

        // The loading guard short-circuits synchronously, so no timer is ever
        // scheduled and no tick can mutate the dots. Assert that scheduling state
        // deterministically instead of waiting a fixed interval to see nothing happen.
        #expect(sut.isAnimating == false)
        #expect(sut.dotScales.allSatisfy { $0 == 0.5 })
    }

    // MARK: - stopAnimation

    @Test("stopAnimation allows the animation to be restarted")
    func stopAnimationPermitsRestart() async {
        let sut = SetupLoaderViewModel()

        sut.startAnimation()
        _ = await waitUntil { sut.dotScales.contains { $0 != 0.5 } }
        sut.stopAnimation()

        // Reset to rest and restart; the guard flag must have been cleared by stopAnimation.
        sut.dotScales = Array(repeating: 0.5, count: 5)
        sut.startAnimation()

        let restarted = await waitUntil { sut.dotScales.contains { $0 != 0.5 } }
        #expect(restarted == true)

        sut.stopAnimation()
    }

    @Test("stopAnimation before any start does not crash")
    func stopAnimationBeforeStartIsSafe() {
        let sut = SetupLoaderViewModel()

        sut.stopAnimation()

        #expect(sut.dotScales.allSatisfy { $0 == 0.5 })
    }
}
