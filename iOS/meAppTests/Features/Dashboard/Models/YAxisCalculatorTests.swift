import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct YAxisCalculatorTests {
    @Test("calculateYAxis: empty data returns fallback scale")
    func emptyDataFallbackScale() {
        let scale = YAxisCalculator.calculateYAxis(
            operations: [],
            goalWeight: nil,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertStoredWeightToDisplay: DashboardTestFixtures.convertToLbs
        )

        #expect(scale.min == 0)
        #expect(scale.max == 100)
        #expect(scale.step == 25)
        #expect(scale.ticks == [0, 25, 50, 75, 100])
        #expect(scale.domain == 0...100)
    }

    @Test("calculateYAxis: empty data with goal uses goal-centric fallback ticks")
    func emptyDataGoalCentricFallback() {
        let scale = YAxisCalculator.calculateYAxis(
            operations: [],
            goalWeight: 178,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertStoredWeightToDisplay: DashboardTestFixtures.convertToLbs
        )

        #expect(scale.ticks == [174, 176, 178, 180, 182])
        #expect(scale.average == 178)
    }

    @Test("calculateYAxis: small dataset computes bounded scale and average")
    func smallDatasetScale() {
        let scale = YAxisCalculator.calculateYAxis(
            operations: [
                DashboardTestFixtures.makeSummary(weight: 1800),
                DashboardTestFixtures.makeSummary(weight: 1820)
            ],
            goalWeight: nil,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertStoredWeightToDisplay: DashboardTestFixtures.convertToLbs
        )

        #expect(scale.average == 181)
        #expect(scale.domain.lowerBound >= 0)
        #expect(scale.ticks.count >= 3)
    }

    @Test("calculateYAxis: large dataset uses the generated scale path")
    func largeDatasetScale() {
        let scale = YAxisCalculator.calculateYAxis(
            operations: [
                DashboardTestFixtures.makeSummary(weight: 1780),
                DashboardTestFixtures.makeSummary(weight: 1800),
                DashboardTestFixtures.makeSummary(weight: 1810),
                DashboardTestFixtures.makeSummary(weight: 1830)
            ],
            goalWeight: nil,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertStoredWeightToDisplay: DashboardTestFixtures.convertToLbs
        )

        #expect(scale.average == 180.5)
        #expect(scale.ticks.count >= 4)
        #expect(scale.domain.lowerBound <= 178)
        #expect(scale.domain.upperBound >= 183)
    }

    @Test("calculateYAxis: weightless mode preserves negative values")
    func weightlessScaleKeepsNegativeDomain() {
        let scale = YAxisCalculator.calculateYAxis(
            operations: [
                DashboardTestFixtures.makeSummary(weight: 1750),
                DashboardTestFixtures.makeSummary(weight: 1800)
            ],
            goalWeight: nil,
            isWeightlessMode: true,
            anchorWeight: 180,
            convertStoredWeightToDisplay: DashboardTestFixtures.convertToLbs
        )

        #expect(scale.average == -2.5)
        #expect(scale.domain.lowerBound < 0)
        #expect(scale.ticks.contains(where: { $0 < 0 }))
    }

    @Test("niceTicks: produces nice snapped ticks and domain")
    func niceTicksProducesSnappedDomain() {
        let result = YAxisCalculator.niceTicks(min: 102, max: 198, desiredTickCount: 4)

        #expect(result.step == 50)
        #expect(result.ticks == [100, 150, 200])
        #expect(result.domain == 100...200)
    }

    @Test("enforceTickLimits: expands overly dense ticks into a smaller set")
    func enforceTickLimitsCompactsTicks() {
        let result = YAxisCalculator.enforceTickLimits(min: 0, max: 10, initialStep: 1)

        #expect(result.step >= 2)
        #expect(result.ticks.count <= 6)
        #expect(result.ticks.first == 0)
        #expect(result.ticks.last == 10)
    }

    @Test("enforceTickLimits and calculateOptimalStep: recover sparse tick sets with readable steps")
    func enforceTickLimitsSparseRangeAndOptimalStep() {
        let result = YAxisCalculator.enforceTickLimits(min: 0, max: 1, initialStep: 10)

        #expect(result.step == 1)
        #expect(result.ticks == [0, 1])
        #expect(YAxisCalculator.calculateOptimalStep(range: 8, targetTickCount: 1) == 8)
        #expect(YAxisCalculator.calculateOptimalStep(range: 18, targetTickCount: 5) == 5)
        #expect(YAxisCalculator.calculateOptimalStep(range: 0.4, targetTickCount: 4) == 1)
    }

    @Test("buildGoalCentricFallback and pickNiceStep: expose stable helper behavior")
    func goalFallbackAndNiceStepHelpers() {
        let fallback = YAxisCalculator.buildGoalCentricFallback(goalWeight: 178)

        #expect(fallback.ticks == [174, 176, 178, 180, 182])
        #expect(fallback.domain == 174...182)
        #expect(YAxisCalculator.pickNiceStep(atLeast: 3.1) == 4)
        #expect(YAxisCalculator.pickNiceStep(atLeast: 19.1) == 20)
    }

    @Test("calculateYAxis: empty data reuses last scale in weightless mode")
    func emptyDataWeightlessUsesLastScale() {
        let lastScale = YAxisScale(
            min: -5,
            max: 5,
            step: 5,
            ticks: [-5, 0, 5],
            domain: -5...5,
            average: -1
        )

        let scale = YAxisCalculator.calculateYAxis(
            operations: [],
            goalWeight: nil,
            isWeightlessMode: true,
            anchorWeight: 180,
            convertStoredWeightToDisplay: DashboardTestFixtures.convertToLbs,
            lastScale: lastScale
        )

        #expect(scale.min == -5)
        #expect(scale.ticks == [-5, 0, 5])
        #expect(scale.average == -1)
    }

    @Test("calculateYAxis: non-weightless mode sanitizes negative converted values")
    func nonWeightlessSanitizesNegativeValues() {
        let scale = YAxisCalculator.calculateYAxis(
            operations: [
                DashboardTestFixtures.makeSummary(weight: 1),
                DashboardTestFixtures.makeSummary(weight: 2),
                DashboardTestFixtures.makeSummary(weight: 3)
            ],
            goalWeight: nil,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertStoredWeightToDisplay: { value in Double(value) - 5 }
        )

        #expect(scale.min == 0)
        #expect(scale.domain.lowerBound == 0)
        #expect(scale.ticks.allSatisfy { $0 >= 0 })
        #expect(scale.average == -3)
    }

    @Test("edge buffer: keep padded domains readable")
    func edgeBufferProducesHeadroom() {
        let buffered = YAxisCalculator.applyEdgeBufferToTicks(
            dataMin: 100,
            dataMax: 110,
            step: 5,
            ticks: [100, 105, 110]
        )

        #expect(buffered.step == 5)
        #expect(buffered.ticks == [95, 100, 105, 110, 115])
    }
}
