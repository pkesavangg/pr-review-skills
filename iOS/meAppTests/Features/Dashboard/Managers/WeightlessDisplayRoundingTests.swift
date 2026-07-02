import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct WeightlessDisplayRoundingTests {

    // MARK: - roundedToDisplayedWeight

    @Test("roundedToDisplayedWeight rounds 14.95 up to 15.0")
    func roundedToDisplayedWeight_roundsUp() {
        #expect(WeightlessDisplayRounding.roundedToDisplayedWeight(14.95) == 15.0)
    }

    @Test("roundedToDisplayedWeight rounds 14.94 down to 14.9")
    func roundedToDisplayedWeight_roundsDown() {
        #expect(WeightlessDisplayRounding.roundedToDisplayedWeight(14.94) == 14.9)
    }

    @Test("roundedToDisplayedWeight rounds negative -14.95 to -15.0")
    func roundedToDisplayedWeight_negative() {
        #expect(WeightlessDisplayRounding.roundedToDisplayedWeight(-14.95) == -15.0)
    }

    @Test("roundedToDisplayedWeight zero stays zero")
    func roundedToDisplayedWeight_zero() {
        #expect(WeightlessDisplayRounding.roundedToDisplayedWeight(0) == 0)
    }

    // MARK: - displayedAverageWeight

    @Test("displayedAverageWeight empty array returns 0")
    func displayedAverageWeight_empty() {
        #expect(WeightlessDisplayRounding.displayedAverageWeight([]) == 0)
    }

    @Test("displayedAverageWeight single value [14.27] returns 14.3")
    func displayedAverageWeight_single() {
        #expect(WeightlessDisplayRounding.displayedAverageWeight([14.27]) == 14.3)
    }

    @Test("displayedAverageWeight [12.1, 47.8] rounds average to 30.0")
    func displayedAverageWeight_twoValues() {
        // Displayed: 12.1, 47.8 → tenths: 121, 478 → sum 599 → avg 299.5 → rounded 300 → 30.0
        #expect(WeightlessDisplayRounding.displayedAverageWeight([12.1, 47.8]) == 30.0)
    }

    // MARK: - displayedWeightlessDifference

    @Test("displayedWeightlessDifference (29.96, 100.01) returns -70.0, not -70.1")
    func displayedWeightlessDifference_kgBugReproduction() {
        // 29.96 rounds to 30.0, 100.01 rounds to 100.0 → 30.0 - 100.0 = -70.0
        let result = WeightlessDisplayRounding.displayedWeightlessDifference(
            currentWeight: 29.96,
            anchorWeight: 100.01
        )
        #expect(result == -70.0)
    }

    @Test("displayedWeightlessDifference equal operands returns 0")
    func displayedWeightlessDifference_equal() {
        let result = WeightlessDisplayRounding.displayedWeightlessDifference(
            currentWeight: 80.0,
            anchorWeight: 80.0
        )
        #expect(result == 0)
    }

    // MARK: - displayedWeightlessAverageDifference

    @Test("displayedWeightlessAverageDifference with empty weights returns 0")
    func displayedWeightlessAverageDifference_empty() {
        let result = WeightlessDisplayRounding.displayedWeightlessAverageDifference(
            currentWeights: [],
            anchorWeight: 80.0
        )
        #expect(result == 0)
    }

    @Test("displayedWeightlessAverageDifference multi-entry matches expected rounding")
    func displayedWeightlessAverageDifference_multiEntry() {
        // Two weights: 82.14, 82.34 → displayed tenths: 821, 823 → avg 822 → rounded 822 → 82.2
        // Anchor: 80.05 → displayed: 80.1
        // Diff: 82.2 - 80.1 = 2.1
        let result = WeightlessDisplayRounding.displayedWeightlessAverageDifference(
            currentWeights: [82.14, 82.34],
            anchorWeight: 80.05
        )
        #expect(result == 2.1)
    }
}
