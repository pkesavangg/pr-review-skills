import Foundation
@testable import meApp
import Testing

/// MOB-1516: unit coverage for the v2-engine `ChartPrep.buildBpm` / `ChartPrep.buildBaby` builders introduced
/// when the BPM and baby dashboard charts were migrated onto the shared `ChartModel` engine (and the legacy
/// `BaseGraphView` stack was deleted). These assert the multi-series contract the renderer AND the VoiceOver
/// descriptor rely on: product type, the `.data` vs `.reference` series roles, draw order, and the fixed
/// reference lines — the shape, not the exact WHO/CDC values (those are covered by the reference-data suites).
@Suite(.serialized)
struct ChartPrepBpmBabyTests {

    // MARK: - Fixtures

    private func date(_ ymd: String) -> Date {
        DateTimeTools.getDateFromDateString(ymd, format: "yyyy-MM-dd")
    }

    private func bpmSummary(
        day: String,
        systolic: Double?,
        diastolic: Double?,
        pulse: Double?
    ) -> BathScaleWeightSummary {
        BathScaleWeightSummary(
            accountId: "acct-1",
            period: day,
            entryTimestamp: "\(day)T08:00:00Z",
            date: date(day),
            count: 1,
            weight: 0,
            pulse: pulse,
            systolic: systolic,
            diastolic: diastolic
        )
    }

    private func babyWeightSummary(day: String, weight: Double) -> BathScaleWeightSummary {
        BathScaleWeightSummary(
            accountId: "acct-1",
            period: day,
            entryTimestamp: "\(day)T08:00:00Z",
            date: date(day),
            count: 1,
            weight: weight
        )
    }

    private func babyHeightSummary(day: String, lengthInches: Double) -> BathScaleWeightSummary {
        BathScaleWeightSummary(
            accountId: "acct-1",
            period: day,
            entryTimestamp: "\(day)T08:00:00Z",
            date: date(day),
            count: 1,
            weight: 0,
            babyLengthInches: lengthInches
        )
    }

    private func babyProfile(sex: String?) -> BabyProfile {
        BabyProfile(
            id: "baby-1",
            name: "Test Baby",
            birthday: date("2026-01-01"),
            biologicalSex: sex
        )
    }

    /// Decigrams → an arbitrary positive display value (magnitude irrelevant to the shape assertions).
    private let convertDecigrams: (Int) -> Double = { Double($0) / 1000.0 }

    // MARK: - buildBpm

    @Test("buildBpm builds systolic/diastolic/pulse as data series with the AHA reference lines")
    func buildBpmProducesDataSeriesAndReferenceLines() {
        let ops = [
            bpmSummary(day: "2026-03-01", systolic: 120, diastolic: 80, pulse: 70),
            bpmSummary(day: "2026-03-02", systolic: 132, diastolic: 86, pulse: 74),
            bpmSummary(day: "2026-03-03", systolic: 118, diastolic: 76, pulse: 66)
        ]

        let model = ChartPrep.buildBpm(operations: ops, period: .week, scrollPosition: ops[0].date)

        #expect(model.productType == .bpm)
        // Draw order is pinned systolic → diastolic → pulse.
        #expect(model.orderedSeriesNames == ["systolic", "diastolic", "pulse"])
        for name in model.orderedSeriesNames {
            #expect(model.style(for: name).role == .data)
            #expect(model.style(for: name).showsPoints)
        }
        // Two dashed AHA reference rules at the normal systolic/diastolic thresholds.
        #expect(model.referenceLines.count == 2)
        #expect(model.referenceLines.allSatisfy { $0.dashed })
        #expect(model.referenceLines.contains { $0.value == Double(BpmConstants.normalSystolic) })
        #expect(model.referenceLines.contains { $0.value == Double(BpmConstants.normalDiastolic) })
        // BPM has no weight-only concepts.
        #expect(model.goalWeight == nil)
        #expect(!model.isEmpty)
    }

    @Test("buildBpm omits a series that has no readings")
    func buildBpmOmitsEmptySeries() {
        let ops = [bpmSummary(day: "2026-03-01", systolic: 120, diastolic: 80, pulse: nil)]

        let model = ChartPrep.buildBpm(operations: ops, period: .week, scrollPosition: ops[0].date)

        #expect(model.orderedSeriesNames == ["systolic", "diastolic"])
        #expect(model.seriesPoints["pulse"] == nil)
    }

    @Test("buildBpm with no readings yields an empty model but keeps the BPM product type")
    func buildBpmEmpty() {
        let model = ChartPrep.buildBpm(operations: [], period: .week, scrollPosition: date("2026-03-01"))

        #expect(model.productType == .bpm)
        #expect(model.orderedSeriesNames.isEmpty)
        #expect(model.isEmpty)
    }

    // MARK: - buildBaby

    @Test("buildBaby (weight) exposes the reading series as data and the percentile curves as reference")
    func buildBabyWeightSeparatesDataAndReference() {
        let ops = [
            babyWeightSummary(day: "2026-02-01", weight: 100),
            babyWeightSummary(day: "2026-03-01", weight: 120),
            babyWeightSummary(day: "2026-04-01", weight: 140)
        ]

        let model = ChartPrep.buildBaby(
            operations: ops,
            period: .total,
            scrollPosition: ops[0].date,
            babyProfile: babyProfile(sex: "male"),
            metric: .weight,
            convertWeight: { $0 },
            convertDecigramsToDisplay: convertDecigrams
        )

        #expect(model.productType == .baby)
        #expect(model.goalWeight == nil)
        // Baby uses percentile CURVES, not fixed horizontal reference rules.
        #expect(model.referenceLines.isEmpty)

        // The real weigh-ins are the single `.data` series.
        let dataNames = model.orderedSeriesNames.filter { model.style(for: $0).role == .data }
        #expect(dataNames == [DashboardStrings.weight])
        #expect(model.style(for: DashboardStrings.weight).showsPoints)

        // The WHO/CDC curves are `.reference`: line-only (no dots), width 1, `baby_percentile_*` named.
        let referenceNames = model.orderedSeriesNames.filter { model.style(for: $0).role == .reference }
        #expect(!referenceNames.isEmpty)
        #expect(referenceNames.allSatisfy { BabyDashboardChartSupport.isPercentileSeries($0) })
        for name in referenceNames {
            #expect(model.style(for: name).showsPoints == false)
            #expect(model.style(for: name).lineWidth == 1)
        }

        // Reference curves are drawn BEHIND the data series (earlier in the draw order).
        let dataIndex = model.orderedSeriesNames.firstIndex(of: DashboardStrings.weight)
        let firstReferenceIndex = model.orderedSeriesNames.firstIndex {
            BabyDashboardChartSupport.isPercentileSeries($0)
        }
        #expect(dataIndex != nil)
        #expect(firstReferenceIndex != nil)
        if let dataIndex, let firstReferenceIndex {
            #expect(firstReferenceIndex < dataIndex)
        }
    }

    @Test("buildBaby (height) uses the recorded length as the data series")
    func buildBabyHeightUsesHeightSeries() {
        let ops = [
            babyHeightSummary(day: "2026-02-01", lengthInches: 20),
            babyHeightSummary(day: "2026-03-01", lengthInches: 22)
        ]

        let model = ChartPrep.buildBaby(
            operations: ops,
            period: .total,
            scrollPosition: ops[0].date,
            babyProfile: babyProfile(sex: "female"),
            metric: .height,
            convertWeight: { $0 },
            convertDecigramsToDisplay: convertDecigrams
        )

        #expect(model.productType == .baby)
        let dataNames = model.orderedSeriesNames.filter { model.style(for: $0).role == .data }
        #expect(dataNames == [BabyDashboardChartSupport.heightSeriesName])
        #expect(model.orderedSeriesNames.contains { BabyDashboardChartSupport.isPercentileSeries($0) })
    }

    @Test("buildBaby with a withheld sex emits no percentile curves (parity)")
    func buildBabyWithheldSexHasNoCurves() {
        let ops = [babyWeightSummary(day: "2026-03-01", weight: 120)]

        let model = ChartPrep.buildBaby(
            operations: ops,
            period: .total,
            scrollPosition: ops[0].date,
            babyProfile: babyProfile(sex: "private"),
            metric: .weight,
            convertWeight: { $0 },
            convertDecigramsToDisplay: convertDecigrams
        )

        #expect(model.productType == .baby)
        #expect(model.orderedSeriesNames.allSatisfy { !BabyDashboardChartSupport.isPercentileSeries($0) })
        // The real reading series still renders.
        #expect(model.orderedSeriesNames.contains(DashboardStrings.weight))
    }
}
