import Foundation
@testable import meApp
import SwiftUI
import Testing

@Suite(.serialized)
@MainActor
struct LoadingDotsViewModelTests {

    // MARK: - Initialization

    @Test("default init applies documented default configuration")
    func defaultInitAppliesDefaults() {
        let sut = LoadingDotsViewModel()

        #expect(sut.dotCount == 3)
        #expect(sut.jumpUpHeight == 6)
        #expect(sut.jumpDownHeight == 2.5)
        #expect(sut.spacing == 4)
        #expect(sut.animationDuration == 1.5)
    }

    @Test("custom init stores provided configuration values")
    func customInitStoresValues() {
        let sut = LoadingDotsViewModel(
            jumpUpHeight: 10,
            jumpDownHeight: 5,
            dotSize: 8,
            spacing: 12,
            animationDuration: 2.0,
            color: .red
        )

        #expect(sut.jumpUpHeight == 10)
        #expect(sut.jumpDownHeight == 5)
        #expect(sut.dotSize == 8)
        #expect(sut.spacing == 12)
        #expect(sut.animationDuration == 2.0)
    }

    // MARK: - delayFor

    @Test("delayFor returns per-index delay scaled by animation duration")
    func delayForReturnsScaledDelays() {
        let sut = LoadingDotsViewModel(animationDuration: 1.5)

        #expect(sut.delayFor(index: 0) == 0.2 / 1.5)
        #expect(sut.delayFor(index: 1) == 0.4 / 1.5)
        #expect(sut.delayFor(index: 2) == 0.55 / 1.5)
    }

    @Test("delayFor returns zero for out-of-range indices")
    func delayForOutOfRangeReturnsZero() {
        let sut = LoadingDotsViewModel()

        #expect(sut.delayFor(index: 3) == 0)
        #expect(sut.delayFor(index: -1) == 0)
    }

    // MARK: - phaseFor

    @Test("phaseFor stays within the 0..<1 normalized range")
    func phaseForStaysNormalized() {
        let sut = LoadingDotsViewModel(animationDuration: 1.5)

        for time in stride(from: 0.0, to: 3.0, by: 0.25) {
            let phase = sut.phaseFor(index: 1, currentTime: time)
            #expect(phase >= 0.0)
            #expect(phase < 1.0)
        }
    }

    @Test("phaseFor is periodic across the animation duration")
    func phaseForIsPeriodic() {
        let sut = LoadingDotsViewModel(animationDuration: 1.5)

        let phaseAtStart = sut.phaseFor(index: 0, currentTime: 0.5)
        let phaseOnePeriodLater = sut.phaseFor(index: 0, currentTime: 0.5 + 1.5)

        #expect(abs(phaseAtStart - phaseOnePeriodLater) < 0.0001)
    }

    // MARK: - interpolatedAnimationState

    @Test("interpolatedAnimationState returns rest keyframe for the initial phase band")
    func interpolatedStateRestBand() {
        let sut = LoadingDotsViewModel()

        let (offset, scale) = sut.interpolatedAnimationState(for: 0.0)

        #expect(offset == 0)
        #expect(scale == 0.8)
    }

    @Test("interpolatedAnimationState returns rest keyframe for phases past the last band")
    func interpolatedStateDefaultBand() {
        let sut = LoadingDotsViewModel()

        let (offset, scale) = sut.interpolatedAnimationState(for: 0.95)

        #expect(offset == 0)
        #expect(scale == 0.8)
    }

    @Test("interpolatedAnimationState interpolates the jump-up band toward peak height")
    func interpolatedStateJumpUpBand() {
        let sut = LoadingDotsViewModel(jumpUpHeight: 6)

        // Start of the 0.4..<0.58 band → still at rest values.
        let (startOffset, startScale) = sut.interpolatedAnimationState(for: 0.4)
        #expect(startOffset == 0)
        #expect(startScale == 0.8)

        // Near the end of the band the dot should be lifting up (negative offset) and scaling up.
        let (midOffset, midScale) = sut.interpolatedAnimationState(for: 0.57)
        #expect(midOffset < 0)
        #expect(midScale > 0.8)
    }

    @Test("interpolatedAnimationState interpolates the descent band toward jump-down height")
    func interpolatedStateDescentBand() {
        let sut = LoadingDotsViewModel(jumpUpHeight: 6, jumpDownHeight: 2.5)

        let (offset, _) = sut.interpolatedAnimationState(for: 0.74)

        // Approaching the bottom of the descent band → offset trends toward the positive jumpDownHeight.
        #expect(offset > 0)
    }

    @Test("interpolatedAnimationState produces continuous values across band boundaries")
    func interpolatedStateContinuousAcrossBands() {
        let sut = LoadingDotsViewModel()

        // Sample near the top boundary between the peak and micro-drop bands (0.58).
        let (offsetBefore, _) = sut.interpolatedAnimationState(for: 0.579)
        let (offsetAfter, _) = sut.interpolatedAnimationState(for: 0.581)

        #expect(abs(offsetBefore - offsetAfter) < 0.5)
    }
}
