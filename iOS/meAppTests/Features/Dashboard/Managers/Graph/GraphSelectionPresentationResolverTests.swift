import Foundation
@testable import meApp
import Testing

/// Deterministic unit tests for `GraphSelectionPresentationResolver` — the pure struct that
/// resolves baby-chart crosshair selection into a `BabyGraphSelectionPresentation`.
///
/// Every input struct is built from verified initializers (`GraphSeries`, `PlottedGraphSeries`,
/// `BabyProfile`) and every expected value is either a literal derived by reading the resolver's
/// math or is computed by calling the same public `BabyDashboardChartSupport` /
/// `BabyPercentileGrowthReference` helpers the resolver delegates to. No `Date()`/randomness is
/// used for values under assertion — dates are fixed and passed explicitly.
@Suite(.serialized)
struct GraphSelectionPresentationResolverTests {

    // MARK: - Fixtures

    private let resolver = GraphSelectionPresentationResolver()

    /// Fixed birthday in 1970 so that `resolvedBirthday(for:)` (which defaults `today` to `Date()`)
    /// always collapses to `startOfDay(birthday)` — the birthday is always earlier than "today".
    private let fixedBirthday = Date(timeIntervalSince1970: 0)

    private func date(_ interval: TimeInterval) -> Date {
        Date(timeIntervalSince1970: interval)
    }

    private func babyProfile() -> BabyProfile {
        BabyProfile(
            id: "baby-1",
            name: "Test Baby",
            birthday: fixedBirthday,
            biologicalSex: "female",
            birthLengthInches: 19.5
        )
    }

    private func plottedPoint(
        series: String,
        value: Double,
        at pointDate: Date
    ) -> PlottedGraphSeries {
        PlottedGraphSeries(
            original: GraphSeries(date: pointDate, value: value, series: series),
            xDate: pointDate
        )
    }

    /// Mirrors the resolver's weight percentile math exactly, so the assertion tracks the
    /// production code path rather than re-implementing the reference tables.
    private func expectedWeightPercentile(
        value: Double,
        on selectionDate: Date,
        unit: WeightUnit,
        profile: BabyProfile
    ) -> Int? {
        let kilograms = unit == .kg ? value : value / 2.20462
        let weightDecigrams = Int((kilograms * BabyPercentileGrowthReference.decigramsToKgFactor).rounded())
        return BabyPercentileGrowthReference.weightPercentile(
            biologicalSex: profile.biologicalSex,
            birthday: BabyDashboardChartSupport.resolvedBirthday(for: profile),
            date: selectionDate,
            weightDecigrams: weightDecigrams
        )
    }

    // MARK: - Guard clauses

    @Test("returns nil when the baby profile is missing")
    func returnsNilWhenBabyProfileMissing() {
        let result = resolver.babySelectionPresentation(
            babyProfile: nil,
            metric: .weight,
            selectedCrosshairDate: date(1_000),
            plottedPoints: [],
            plotXDate: { $0 },
            currentUnit: .kg,
            displayWeight: 10
        )
        #expect(result == nil)
    }

    @Test("returns nil when there is no selected crosshair date")
    func returnsNilWhenCrosshairDateMissing() {
        let result = resolver.babySelectionPresentation(
            babyProfile: babyProfile(),
            metric: .weight,
            selectedCrosshairDate: nil,
            plottedPoints: [],
            plotXDate: { $0 },
            currentUnit: .kg,
            displayWeight: 10
        )
        #expect(result == nil)
    }

    // MARK: - Exact value match

    @Test("resolves the exact weight value when a plotted point lands on the plotted date")
    func resolvesExactWeightValue() throws {
        let profile = babyProfile()
        let crosshairDate = date(84 * 86_400)
        let points = [
            plottedPoint(series: DashboardStrings.weight, value: 3.0, at: date(83 * 86_400)),
            plottedPoint(series: DashboardStrings.weight, value: 5.0, at: crosshairDate),
            plottedPoint(series: DashboardStrings.weight, value: 6.0, at: date(85 * 86_400))
        ]

        let result = try #require(
            resolver.babySelectionPresentation(
                babyProfile: profile,
                metric: .weight,
                selectedCrosshairDate: crosshairDate,
                plottedPoints: points,
                plotXDate: { $0 },
                currentUnit: .kg,
                displayWeight: 99
            )
        )

        #expect(result.crosshairDate == crosshairDate)
        #expect(result.crosshairValue == 5.0)
        #expect(result.percentile == expectedWeightPercentile(
            value: 5.0,
            on: crosshairDate,
            unit: .kg,
            profile: profile
        ))
    }

    @Test("resolves the exact height value and its non-optional percentile")
    func resolvesExactHeightValue() throws {
        let profile = babyProfile()
        let crosshairDate = date(84 * 86_400)
        let heightSeries = "baby_height"
        let points = [
            plottedPoint(series: heightSeries, value: 22.0, at: date(70 * 86_400)),
            plottedPoint(series: heightSeries, value: 24.0, at: crosshairDate)
        ]

        let result = try #require(
            resolver.babySelectionPresentation(
                babyProfile: profile,
                metric: .height,
                selectedCrosshairDate: crosshairDate,
                plottedPoints: points,
                plotXDate: { $0 },
                currentUnit: .kg,
                displayWeight: nil
            )
        )

        #expect(result.crosshairValue == 24.0)
        let expectedPercentile = BabyDashboardChartSupport.heightPercentile(
            for: profile,
            heightInches: 24.0,
            on: crosshairDate
        )
        #expect(result.percentile == expectedPercentile)
    }

    // MARK: - Interpolation

    @Test("interpolates the primary value between two bracketing points")
    func interpolatesWeightBetweenPoints() throws {
        let profile = babyProfile()
        // Bracketing points at t=1000 (10) and t=2000 (20); selection at the midpoint t=1500
        // has no exact match, so progress = 0.5 -> 10 + (20 - 10) * 0.5 = 15.
        let crosshairDate = date(1_500)
        let points = [
            plottedPoint(series: DashboardStrings.weight, value: 10.0, at: date(1_000)),
            plottedPoint(series: DashboardStrings.weight, value: 20.0, at: date(2_000))
        ]

        let result = try #require(
            resolver.babySelectionPresentation(
                babyProfile: profile,
                metric: .weight,
                selectedCrosshairDate: crosshairDate,
                plottedPoints: points,
                plotXDate: { $0 },
                currentUnit: .kg,
                displayWeight: nil
            )
        )

        #expect(result.crosshairValue == 15.0)
        #expect(result.crosshairDate == crosshairDate)
    }

    @Test("uses plotXDate to map the crosshair onto a point before matching")
    func matchesUsingPlotXDateMapping() throws {
        let profile = babyProfile()
        // The raw crosshair date matches no point, but plotXDate maps it onto the t=1000 point,
        // yielding an exact match (10) while the presentation keeps the original crosshair date.
        let crosshairDate = date(9_999)
        let points = [
            plottedPoint(series: DashboardStrings.weight, value: 10.0, at: date(1_000)),
            plottedPoint(series: DashboardStrings.weight, value: 20.0, at: date(2_000))
        ]

        let result = try #require(
            resolver.babySelectionPresentation(
                babyProfile: profile,
                metric: .weight,
                selectedCrosshairDate: crosshairDate,
                plottedPoints: points,
                plotXDate: { _ in self.date(1_000) },
                currentUnit: .kg,
                displayWeight: nil
            )
        )

        #expect(result.crosshairValue == 10.0)
        #expect(result.crosshairDate == crosshairDate)
    }

    // MARK: - Weight fallback

    @Test("falls back to the display weight when no points are available")
    func fallsBackToDisplayWeightWhenNoPoints() throws {
        let profile = babyProfile()
        let crosshairDate = date(84 * 86_400)

        let result = try #require(
            resolver.babySelectionPresentation(
                babyProfile: profile,
                metric: .weight,
                selectedCrosshairDate: crosshairDate,
                plottedPoints: [],
                plotXDate: { $0 },
                currentUnit: .kg,
                displayWeight: 8.0
            )
        )

        #expect(result.crosshairValue == 8.0)
        #expect(result.percentile == expectedWeightPercentile(
            value: 8.0,
            on: crosshairDate,
            unit: .kg,
            profile: profile
        ))
    }

    @Test("ignores points whose series does not match the metric and falls back")
    func fallsBackWhenSeriesDoesNotMatchMetric() throws {
        let profile = babyProfile()
        let crosshairDate = date(84 * 86_400)
        // A height-series point is filtered out for a weight metric, forcing the display-weight fallback.
        let points = [
            plottedPoint(series: "baby_height", value: 22.0, at: crosshairDate)
        ]

        let result = try #require(
            resolver.babySelectionPresentation(
                babyProfile: profile,
                metric: .weight,
                selectedCrosshairDate: crosshairDate,
                plottedPoints: points,
                plotXDate: { $0 },
                currentUnit: .kg,
                displayWeight: 7.5
            )
        )

        #expect(result.crosshairValue == 7.5)
    }

    @Test("returns nil when the weight fallback has no display weight")
    func returnsNilWhenWeightFallbackHasNoDisplayWeight() {
        let result = resolver.babySelectionPresentation(
            babyProfile: babyProfile(),
            metric: .weight,
            selectedCrosshairDate: date(84 * 86_400),
            plottedPoints: [],
            plotXDate: { $0 },
            currentUnit: .kg,
            displayWeight: nil
        )
        #expect(result == nil)
    }

    @Test("returns nil when the weight fallback display weight is effectively zero")
    func returnsNilWhenWeightFallbackDisplayWeightIsZero() {
        let result = resolver.babySelectionPresentation(
            babyProfile: babyProfile(),
            metric: .weight,
            selectedCrosshairDate: date(84 * 86_400),
            plottedPoints: [],
            plotXDate: { $0 },
            currentUnit: .kg,
            displayWeight: 0
        )
        #expect(result == nil)
    }

    @Test("converts pounds to kilograms for the weight percentile")
    func convertsPoundsToKilogramsForPercentile() throws {
        let profile = babyProfile()
        let crosshairDate = date(120 * 86_400)

        let result = try #require(
            resolver.babySelectionPresentation(
                babyProfile: profile,
                metric: .weight,
                selectedCrosshairDate: crosshairDate,
                plottedPoints: [],
                plotXDate: { $0 },
                currentUnit: .lb,
                displayWeight: 15.0
            )
        )

        #expect(result.crosshairValue == 15.0)
        #expect(result.percentile == expectedWeightPercentile(
            value: 15.0,
            on: crosshairDate,
            unit: .lb,
            profile: profile
        ))
    }

    // MARK: - Height fallback

    @Test("returns nil for height when no recorded length points are available (no synthetic fill)")
    func returnsNilForHeightWhenNoPoints() {
        let profile = babyProfile()
        let crosshairDate = date(84 * 86_400)

        let result = resolver.babySelectionPresentation(
            babyProfile: profile,
            metric: .height,
            selectedCrosshairDate: crosshairDate,
            plottedPoints: [],
            plotXDate: { $0 },
            currentUnit: .kg,
            displayWeight: nil
        )

        #expect(result == nil)
    }
}
