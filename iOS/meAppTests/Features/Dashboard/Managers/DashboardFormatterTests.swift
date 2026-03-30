import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct DashboardFormatterTests {
    private func makeSUT() -> DashboardFormatter {
        DashboardFormatter()
    }

    @Test("formatYAxisTickLabel and roundedGoalWeight: round away from zero and add grouping")
    func yAxisFormatting() {
        let sut = makeSUT()

        #expect(sut.roundedGoalWeight(180.5) == 181)
        #expect(sut.roundedGoalWeight(-180.5) == -181)
        #expect(sut.formatYAxisTickLabel(1234.6) == "1,235")
    }

    @Test("formatChartDate: uses day labels for week month and month-year for year total")
    func chartDateFormatting() {
        let sut = makeSUT()
        let date = makeDate("2026-03-10")

        #expect(sut.formatChartDate(date, period: .week) == "Mar 10")
        #expect(sut.formatChartDate(date, period: .month) == "Mar 10")
        #expect(sut.formatChartDate(date, period: .year) == "Mar 2026")
        #expect(sut.formatChartDate(date, period: .total) == "Mar 2026")
    }

    @Test("formatMetricInfoSingleDate: uses full day for week month and month-year for year total")
    func metricInfoSingleDateFormatting() {
        let sut = makeSUT()
        let date = makeDate("2026-03-10")

        #expect(sut.formatMetricInfoSingleDate(date, period: .week) == "Mar 10, 2026")
        #expect(sut.formatMetricInfoSingleDate(date, period: .year) == "Mar 2026")
    }

    @Test("metric info date label: history date preserves history prefix and casing")
    func metricInfoDateLabelForHistory() {
        let sut = makeSUT()

        let label = sut.formatMetricInfoDateLabel(
            entryDate: makeDate("2026-03-10"),
            isFromHistory: true,
            period: .week,
            selectedPointDate: nil,
            crosshairDate: nil,
            weightLabel: "ignored"
        )

        #expect(label == "Measurement taken March 10, 2026")
    }

    @Test("metric info date label: selected point and crosshair use period-specific selection prefix")
    func metricInfoDateLabelForSelections() {
        let sut = makeSUT()
        let pointLabel = sut.formatMetricInfoDateLabel(
            entryDate: nil,
            isFromHistory: false,
            period: .year,
            selectedPointDate: makeDate("2026-03-10"),
            crosshairDate: nil,
            weightLabel: "ignored"
        )
        let crosshairLabel = sut.formatMetricInfoDateLabel(
            entryDate: nil,
            isFromHistory: false,
            period: .week,
            selectedPointDate: nil,
            crosshairDate: makeDate("2026-03-10"),
            weightLabel: "ignored"
        )

        #expect(pointLabel == "month average mar 2026")
        #expect(crosshairLabel == "day average mar 10, 2026")
    }

    @Test("metric info date label: fallback uses weight label when no explicit date exists")
    func metricInfoDateLabelFallback() {
        let sut = makeSUT()

        let label = sut.formatMetricInfoDateLabel(
            entryDate: nil,
            isFromHistory: false,
            period: .total,
            selectedPointDate: nil,
            crosshairDate: nil,
            weightLabel: "Jan 2026 - Mar 2026"
        )

        #expect(label == "total average jan 2026 - mar 2026")
    }

    @Test("parseEntryDate: supports fractional and plain ISO8601 timestamps")
    func parseEntryDateVariants() {
        let sut = makeSUT()
        let fractional = makeDTO(date: nil, entryTimestamp: "2026-03-10T08:30:45.123Z")
        let plain = makeDTO(date: nil, entryTimestamp: "2026-03-10T08:30:45Z")

        #expect(sut.parseEntryDate(from: fractional) != nil)
        #expect(sut.parseEntryDate(from: plain) != nil)
    }

    @Test("parseEntryDate: nil or invalid timestamps return nil")
    func parseEntryDateInvalidInput() {
        let sut = makeSUT()

        #expect(sut.parseEntryDate(from: makeDTO(date: nil, entryTimestamp: nil)) == nil)
        #expect(sut.parseEntryDate(from: makeDTO(date: nil, entryTimestamp: "not-a-date")) == nil)
    }

    @Test("isDashboardEntry: matches dashboard source only")
    func dashboardSourceCheck() {
        let sut = makeSUT()

        #expect(sut.isDashboardEntry(makeDTO(source: "dashboard")) == true)
        #expect(sut.isDashboardEntry(makeDTO(source: "bluetooth")) == false)
    }

    @Test("formattedMetricValue: placeholder and numeric zero collapse to placeholder output")
    func formattedMetricValuePlaceholderCases() {
        let sut = makeSUT()

        #expect(sut.formattedMetricValue(for: (preLabel: nil, value: DashboardStrings.placeholder)) == DashboardStrings.placeholder)
        #expect(sut.formattedMetricValue(for: (preLabel: DashboardStrings.visceralFatPre, value: "0")) == "\(DashboardStrings.visceralFatPre) \(DashboardStrings.placeholder)")
        #expect(sut.formattedMetricValue(for: (preLabel: DashboardStrings.visceralFatPre, value: "0.0")) == "\(DashboardStrings.visceralFatPre) \(DashboardStrings.placeholder)")
    }

    @Test("formattedMetricValue: non-zero values preserve prelabel and whitespace-trimmed values")
    func formattedMetricValueNonZero() {
        let sut = makeSUT()

        #expect(sut.formattedMetricValue(for: (preLabel: DashboardStrings.visceralFatPre, value: "11")) == "\(DashboardStrings.visceralFatPre) 11")
        #expect(sut.formattedMetricValue(for: (preLabel: nil, value: " 72 ")) == " 72 ")
    }

    @Test("helper methods: metric info labels are lowercased and selection prefixes match the period")
    func helperMethods() {
        let sut = makeSUT()

        #expect(sut.composeMetricInfoLabel(prefix: "Day Average", dateText: "Mar 10, 2026") == "day average mar 10, 2026")
        #expect(sut.selectionPrefix(for: .week) == "day average")
        #expect(sut.selectionPrefix(for: .total) == "month average")
    }

    private func makeDate(_ value: String) -> Date {
        DateTimeTools.getDateFromDateString(value, format: "yyyy-MM-dd")
    }

    private func makeDTO(
        date: Date? = nil,
        entryTimestamp: String? = nil,
        source: String? = nil
    ) -> BathScaleOperationDTO {
        let resolvedTimestamp = entryTimestamp ?? date.map { DateTimeTools.isoFormatter().string(from: $0) }
        return BathScaleOperationDTO(
            accountId: "acct-1",
            bmr: nil,
            bmi: nil,
            bodyFat: nil,
            boneMass: nil,
            entryTimestamp: resolvedTimestamp,
            entryType: nil,
            impedance: nil,
            metabolicAge: nil,
            muscleMass: nil,
            operationType: nil,
            proteinPercent: nil,
            pulse: nil,
            serverTimestamp: nil,
            skeletalMusclePercent: nil,
            source: source,
            subcutaneousFatPercent: nil,
            systolic: nil,
            diastolic: nil,
            meanArterial: nil,
            unit: nil,
            visceralFatLevel: nil,
            water: nil,
            weight: 1800
        )
    }
}
