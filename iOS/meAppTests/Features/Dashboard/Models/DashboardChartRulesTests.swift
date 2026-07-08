import Foundation
@testable import meApp
import SwiftUI
import Testing

/// Pure-logic tests for `DashboardChartRules` — the right-aligned 7-day chart window
/// (`DashboardSnapshotChartWindow`), the Y-axis scale providers (`DashboardChartScaleProvider`),
/// and the series-color rules (`DashboardChartStyleProvider`). All inputs are deterministic.
@Suite(.serialized)
@MainActor
struct DashboardChartRulesTests {

    // MARK: - Helpers

    private let calendar = Calendar.current

    private func day(_ offset: Int, from base: Date) -> Date {
        calendar.date(byAdding: .day, value: offset, to: base) ?? base
    }

    /// Builds a summary anchored on a specific day with a unique entryTimestamp.
    private func summary(dayOffset: Int, from base: Date, weight: Double = 1800) -> BathScaleWeightSummary {
        let date = day(dayOffset, from: base)
        return DashboardTestFixtures.makeSummary(
            entryTimestamp: "ts-\(dayOffset)",
            date: date,
            weight: weight
        )
    }

    private func bpSummary(systolic: Double?, diastolic: Double?, pulse: Double?) -> BathScaleWeightSummary {
        BathScaleWeightSummary(
            accountId: "acct-1",
            period: "2026-03-01",
            entryTimestamp: "2026-03-01T08:00:00Z",
            date: Date(),
            count: 1,
            weight: 0,
            pulse: pulse,
            systolic: systolic,
            diastolic: diastolic
        )
    }

    // MARK: - DashboardSnapshotChartWindow.make

    @Test("window: empty summaries returns nil")
    func windowEmptyReturnsNil() {
        let result = DashboardSnapshotChartWindow.make(summaries: []) { _ in true }
        #expect(result == nil)
    }

    @Test("window: everything excluded by include returns nil")
    func windowAllExcludedReturnsNil() {
        let base = calendar.startOfDay(for: Date())
        let summaries = [summary(dayOffset: 0, from: base)]
        let result = DashboardSnapshotChartWindow.make(summaries: summaries) { _ in false }
        #expect(result == nil)
    }

    @Test("window: bounds span a 7-day window ending the day after the latest entry")
    func windowBounds() {
        let base = calendar.startOfDay(for: Date())
        let summaries = [summary(dayOffset: 0, from: base)]

        guard let result = DashboardSnapshotChartWindow.make(summaries: summaries) { _ in true } else {
            Issue.record("Expected a non-nil window")
            return
        }

        let expectedEnd = day(1, from: base)
        let expectedStart = day(-6, from: base)
        #expect(result.bounds.start == expectedStart)
        #expect(result.bounds.end == expectedEnd)
        #expect(result.visibleSummaries.count == 1)
        #expect(result.chartSummaries.count == 1)
    }

    @Test("window: prepends the previous out-of-window point and keeps visible points")
    func windowIncludesPrevious() {
        let base = calendar.startOfDay(for: Date())
        // latest = day 0; window = [day-6, day+1). previous = day-10, visible = day-3 & day 0.
        let previous = summary(dayOffset: -10, from: base, weight: 1700)
        let visibleA = summary(dayOffset: -3, from: base, weight: 1780)
        let latest = summary(dayOffset: 0, from: base, weight: 1800)
        let summaries = [latest, previous, visibleA] // intentionally unsorted

        guard let result = DashboardSnapshotChartWindow.make(summaries: summaries) { _ in true } else {
            Issue.record("Expected a non-nil window")
            return
        }

        #expect(result.visibleSummaries.count == 2)
        #expect(result.chartSummaries.count == 3)
        // Previous point is prepended so the line enters the window from the left.
        #expect(result.chartSummaries.first?.id == previous.id)
        #expect(result.chartSummaries.last?.id == latest.id)
        // The out-of-window previous point is not part of the visible set.
        #expect(!result.visibleSummaries.contains { $0.id == previous.id })
    }

    @Test("window: latest point defines the right edge and stays in the visible set")
    func windowLatestDefinesRightEdge() {
        let base = calendar.startOfDay(for: Date())
        // The window is right-aligned to the max date (day+3 here), so that point always lands
        // inside the visible set. The earlier day-2/day 0 points also fall in the 7-day window;
        // day-9 falls before weekStart and is prepended as the "previous" point.
        let previous = summary(dayOffset: -9, from: base, weight: 1700)
        let visibleA = summary(dayOffset: -2, from: base, weight: 1780)
        let visibleB = summary(dayOffset: 0, from: base, weight: 1800)
        let latest = summary(dayOffset: 3, from: base, weight: 1850)
        let summaries = [previous, visibleA, visibleB, latest]

        guard let result = DashboardSnapshotChartWindow.make(summaries: summaries) { _ in true } else {
            Issue.record("Expected a non-nil window")
            return
        }

        #expect(result.bounds.end == day(4, from: base))
        #expect(result.bounds.start == day(-3, from: base))
        #expect(result.visibleSummaries.count == 3)
        #expect(result.visibleSummaries.contains { $0.id == latest.id })
        // day-9 is prepended (previous), so the combined series has one more point than visible.
        #expect(result.chartSummaries.first?.id == previous.id)
        #expect(result.chartSummaries.count == result.visibleSummaries.count + 1)
    }

    // MARK: - DashboardChartScaleProvider.weightScale

    @Test("weightScale: empty operations with no goal yields the default 0...100 fallback")
    func weightScaleEmptyNoGoal() {
        let scale = DashboardChartScaleProvider.weightScale(
            operations: [],
            goalWeight: nil
        ) { $0 }
        #expect(scale.min == 0)
        #expect(scale.max == 100)
        #expect(scale.step == 25)
        #expect(scale.ticks == [0, 25, 50, 75, 100])
    }

    @Test("weightScale: empty operations with a goal yields a goal-centric fallback")
    func weightScaleEmptyWithGoal() {
        let scale = DashboardChartScaleProvider.weightScale(
            operations: [],
            goalWeight: 150
        ) { $0 }
        // buildGoalCentricFallback(150): step 2, centered on 150.
        #expect(scale.ticks == [146, 148, 150, 152, 154])
        #expect(scale.step == 2)
        #expect(scale.average == 150)
        #expect(scale.min == 146)
        #expect(scale.max == 154)
    }

    // MARK: - DashboardChartScaleProvider.babyWeightScale

    @Test("babyWeightScale: empty operations returns the 0...30 default scale")
    func babyWeightScaleEmpty() {
        let scale = DashboardChartScaleProvider.babyWeightScale(
            operations: []
        ) { Double($0) }
        #expect(scale.min == 0)
        #expect(scale.max == 30)
        #expect(scale.step == 10)
        #expect(scale.ticks == [0, 10, 20, 30])
        #expect(scale.average == 15)
    }

    @Test("babyWeightScale: all non-positive weights fall back to the default scale")
    func babyWeightScaleAllZero() {
        let ops = [
            DashboardTestFixtures.makeSummary(weight: 0),
            DashboardTestFixtures.makeSummary(weight: 0)
        ]
        let scale = DashboardChartScaleProvider.babyWeightScale(
            operations: ops
        ) { Double($0) }
        #expect(scale.ticks == [0, 10, 20, 30])
    }

    @Test("babyWeightScale: computes a padded, nice-stepped scale from real weights")
    func babyWeightScaleRealValues() {
        let ops = [
            DashboardTestFixtures.makeSummary(weight: 10),
            DashboardTestFixtures.makeSummary(weight: 20),
            DashboardTestFixtures.makeSummary(weight: 30)
        ]
        let scale = DashboardChartScaleProvider.babyWeightScale(
            operations: ops
        ) { Double($0) }
        // min 10, max 30 → padding 3 → paddedMin 7, paddedMax 33 → step 7 → ticks 7...35.
        #expect(scale.min == 7)
        #expect(scale.max == 35)
        #expect(scale.step == 7)
        #expect(scale.ticks == [7, 14, 21, 28, 35])
        #expect(scale.average == 20)
    }

    // MARK: - DashboardChartScaleProvider.bpmScale

    @Test("bpmScale: empty operations returns the BPM default scale")
    func bpmScaleEmpty() {
        let scale = DashboardChartScaleProvider.bpmScale(from: [])
        #expect(scale.min == BpmConstants.defaultYMin)
        #expect(scale.max == BpmConstants.defaultYMax)
        #expect(scale.step == 40)
        #expect(scale.ticks == [40, 80, 120, 160, 200])
        #expect(scale.average == 120)
    }

    @Test("bpmScale: operations with no BP values fall back to the default scale")
    func bpmScaleNoValues() {
        let ops = [bpSummary(systolic: nil, diastolic: nil, pulse: nil)]
        let scale = DashboardChartScaleProvider.bpmScale(from: ops)
        #expect(scale.ticks == [40, 80, 120, 160, 200])
    }

    @Test("bpmScale: computes a padded, 10-stepped scale from systolic/diastolic/pulse")
    func bpmScaleRealValues() {
        let ops = [bpSummary(systolic: 120, diastolic: 80, pulse: 60)]
        let scale = DashboardChartScaleProvider.bpmScale(from: ops)
        // values 60...120 → padded 50...130 → step 20 → ticks 40...140.
        #expect(scale.min == 40)
        #expect(scale.max == 140)
        #expect(scale.step == 20)
        #expect(scale.ticks == [40, 60, 80, 100, 120, 140])
        #expect(scale.average == 260.0 / 3.0)
    }

    // MARK: - DashboardChartStyleProvider.seriesColors

    private var palette: AppColors.Palette { AppColors.Theme.primary.palette }

    @Test("seriesColors: bpm pulse series uses the subheading color for both line and point")
    func seriesColorsBpmPulse() {
        let theme = palette
        let colors = DashboardChartStyleProvider.seriesColors(
            for: "pulse",
            productType: .bpm,
            theme: theme
        )
        #expect(colors.line == theme.textSubheading)
        #expect(colors.point == theme.textSubheading)
    }

    @Test("seriesColors: bpm non-pulse series uses the AHA classification color")
    func seriesColorsBpmClassification() {
        let theme = palette
        let normalColor = AhaPressureClass.normal.color(theme: theme)

        // Explicit classification.
        let normal = DashboardChartStyleProvider.seriesColors(
            for: "systolic",
            productType: .bpm,
            theme: theme,
            bpmClassification: .normal
        )
        #expect(normal.line == normalColor)
        #expect(normal.point == normalColor)

        // Nil classification defaults to .normal.
        let defaulted = DashboardChartStyleProvider.seriesColors(
            for: "systolic",
            productType: .bpm,
            theme: theme,
            bpmClassification: nil
        )
        #expect(defaulted.line == normalColor)

        // Line color is unaffected by the outside-month flag (only the point dims).
        let outside = DashboardChartStyleProvider.seriesColors(
            for: "systolic",
            productType: .bpm,
            theme: theme,
            bpmClassification: .hypertensionStage2,
            isOutsideMonthInterval: true
        )
        #expect(outside.line == AhaPressureClass.hypertensionStage2.color(theme: theme))
    }

    @Test("seriesColors: weight series uses the weight-scale brand color")
    func seriesColorsWeight() {
        let theme = palette
        let colors = DashboardChartStyleProvider.seriesColors(
            for: DashboardStrings.weight,
            productType: .scale,
            theme: theme
        )
        #expect(colors.line == theme.weightScaleColor)
        #expect(colors.point == theme.weightScaleColor)
    }

    @Test("seriesColors: non-weight metric series uses the primary action color")
    func seriesColorsMetric() {
        let theme = palette

        let inside = DashboardChartStyleProvider.seriesColors(
            for: "bodyFat",
            productType: .scale,
            theme: theme,
            isOutsideMonthInterval: false
        )
        #expect(inside.line == theme.actionPrimary)
        #expect(inside.point == theme.actionPrimary)

        let outside = DashboardChartStyleProvider.seriesColors(
            for: "bodyFat",
            productType: .scale,
            theme: theme,
            isOutsideMonthInterval: true
        )
        #expect(outside.line == theme.actionPrimary)
        #expect(outside.point == theme.actionPrimaryDisabled)
    }
}
