//
//  GraphView.swift
//  meApp
//
//  Created by Lakshmi Priya on 10/06/25.
//

import SwiftUI
import Charts

struct GraphView: View {
    let operations: [BathScaleOperationDTO]
    let selectedSegmentTitle: String
    @Binding var selectedWeight: Double?
    @StateObject private var viewModel = GraphViewModel()
    @Environment(\.appTheme) private var theme

    private let weekDaysAbbr = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
    private let yearMonthsInitial = ["J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"]

    var body: some View {
        ZStack(alignment: .trailing) {
            Chart {
                // Solid Y-axis grid lines (overlayed for control)
                ForEach(viewModel.yAxisTicks, id: \.self) { tick in
                    RuleMark(y: .value("Y Grid", tick))
                        .lineStyle(StrokeStyle(lineWidth: 1))
                        .foregroundStyle(theme.statusUtility)
                        .zIndex(-1)
                }
                // Dotted X-axis grid lines
                ForEach(xAxisGridPositions(), id: \.self) { position in
                    RuleMark(x: .value("X Grid", position))
                        .lineStyle(StrokeStyle(lineWidth: 1, dash: [3, 3]))
                        .foregroundStyle(theme.statusUtility)
                        .zIndex(-1)
                }
                chartLineMarks()
                chartPointMarks()
            }
            .chartYScale(domain: 175...190)
            .chartXScale(domain: xAxisDomain())
            .chartYAxis {
                AxisMarks(values: .automatic) { value in
                    AxisGridLine()
                        .foregroundStyle(theme.statusUtility)
                    AxisTick()
                    AxisValueLabel {
                        if let doubleValue = value.as(Double.self) {
                            Text("\(Int(doubleValue))")
                                .fontOpenSans(.subHeading2)
                                .zIndex(1)
                        }
                    }
                }
            }
            .chartXAxis {
                AxisMarks(values: xAxisLabels()) { value in
                    AxisGridLine()
                    AxisTick()
                    AxisValueLabel {
                        if let date = value.as(Date.self) {
                            xLabel(for: date)
                        }
                    }
                }
            }
            .frame(height: 300)
            .background(
                GeometryReader { geo in
                    theme.textInverse
                        .onAppear { viewModel.chartHeight = geo.size.height }
                        .onChange(of: geo.size.height) {
                            viewModel.chartHeight = geo.size.height
                        }
                }
            )
            .padding(.top, 16)
            .chartOverlay { proxy in
                GeometryReader { _ in
                    Color.clear
                        .contentShape(Rectangle())
                        .gesture(
                            viewModel.dragGesture(
                                proxy: proxy,
                                operations: operations,
                                selectedWeight: $selectedWeight
                            )
                        )
                }
            }
            .onPreferenceChange(AnnotationHeightKey.self) { viewModel.annotationHeight = $0 }
            .zIndex(1)
        }
    }

    // MARK: - X Axis Helpers

    private func xAxisDomain() -> ClosedRange<Date> {
        switch selectedSegmentTitle {
        case TimePeriod.week.displayName:
            // Always show Sunday to Saturday for week
            guard let start = weekStartDate() else { return Date()...Date() }
            let end = Calendar.current.date(byAdding: .day, value: 6, to: start)!
            return start...end
        case TimePeriod.year.displayName:
            // Always show Jan to Dec for year
            guard let start = yearStartDate() else { return Date()...Date() }
            let end = Calendar.current.date(byAdding: .month, value: 11, to: start)!
            return start...end
        case TimePeriod.month.displayName:
            // Always show the full month for month
            guard let start = monthStartDate() else { return Date()...Date() }
            let end = Calendar.current.date(byAdding: .month, value: 1, to: start)!
            let lastDay = Calendar.current.date(byAdding: .day, value: -1, to: end)!
            return start...lastDay
        default:
            return viewModel.xAxisDomain(for: operations)
        }
    }

    private func xAxisLabels() -> [Date] {
        switch selectedSegmentTitle {
        case TimePeriod.week.displayName:
            return weekLabels()
        case TimePeriod.month.displayName:
            return monthLabels()
        case TimePeriod.year.displayName:
            return yearLabels()
        default:
            return []
        }
    }

    private func xAxisGridPositions() -> [Date] {
        xAxisLabels()
    }

    private func weekStartDate() -> Date? {
        // Use current week or week of last entry if exists
        let referenceDate = operations.last?.date ?? Date()
        return referenceDate.startOfWeek
    }

    private func yearStartDate() -> Date? {
        // Use current year or year of last entry if exists
        let referenceDate = operations.last?.date ?? Date()
        let cal = Calendar.current
        let comps = cal.dateComponents([.year], from: referenceDate)
        return cal.date(from: comps)
    }

    private func monthStartDate() -> Date? {
        // Use current month or month of last entry if exists
        let referenceDate = operations.last?.date ?? Date()
        let cal = Calendar.current
        let comps = cal.dateComponents([.year, .month], from: referenceDate)
        return cal.date(from: comps)
    }

    private func weekLabels() -> [Date] {
        guard let start = weekStartDate() else { return [] }
        return (0..<7).compactMap { Calendar.current.date(byAdding: .day, value: $0, to: start) }
    }

    private func monthLabels() -> [Date] {
        guard let start = monthStartDate() else { return [] }
        var labels: [Date] = []
        let cal = Calendar.current
        if let range = cal.range(of: .day, in: .month, for: start) {
            let daysInMonth = range.count
            // Split into 4 weeks, label as [1, 8, 15, 22]
            let days = [1, 8, 15, 22]
            for d in days {
                // Clamp to last day of the month
                let day = min(d, daysInMonth)
                if let labelDate = cal.date(bySetting: .day, value: day, of: start) {
                    labels.append(labelDate)
                }
            }
        }
        return labels
    }

    private func yearLabels() -> [Date] {
        guard let start = yearStartDate() else { return [] }
        return (0..<12).compactMap { Calendar.current.date(byAdding: .month, value: $0, to: start) }
    }

    @ViewBuilder
    private func xLabel(for date: Date) -> some View {
        switch selectedSegmentTitle {
        case TimePeriod.week.displayName:
            let weekday = Calendar.current.component(.weekday, from: date)
            Text(weekDaysAbbr[(weekday - 1) % 7])
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
        case TimePeriod.month.displayName:
            let day = Calendar.current.component(.day, from: date)
            Text("\(day)")
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
        case TimePeriod.year.displayName:
            let month = Calendar.current.component(.month, from: date)
            Text(yearMonthsInitial[(month - 1) % 12])
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
        default:
            EmptyView()
        }
    }

    // MARK: - ChartContentBuilders

    @ChartContentBuilder
    private func chartRuleMark(date: Date, weight: Double, selected: BathScaleOperationDTO) -> some ChartContent {
        RuleMark(x: .value("Selected Date", date))
            .lineStyle(StrokeStyle(lineWidth: 1))
            .foregroundStyle(theme.statusUtility)
            .annotation(position: .top, alignment: viewModel.ruleMarkAlignment(for: selected, in: operations)) {
                ruleMarkDateLabel(date: date)
            }
            .annotation(position: .bottom, alignment: viewModel.ruleMarkAlignment(for: selected, in: operations)) {
                let offsetY = viewModel.annotationBubbleOffset()
                Text("\(Int(weight))")
                    .fontWeight(.bold)
                    .fontOpenSans(.body3)
                    .foregroundColor(theme.textInverse)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 1)
                    .background(Capsule().fill(theme.statusSuccess))
                    .background(
                        GeometryReader { bubbleGeo in
                            theme.textInverse
                                .preference(key: AnnotationHeightKey.self, value: bubbleGeo.size.height)
                        }
                    )
                    .offset(y: offsetY)
                    .zIndex(100)
            }
    }

    @ChartContentBuilder
    private func chartLineMarks() -> some ChartContent {
        ForEach(operations) { entry in
            if let date = entry.date, let weight = entry.weight {
                LineMark(
                    x: .value("Date", date),
                    y: .value("Weight", weight)
                )
                .interpolationMethod(.catmullRom)
                .foregroundStyle(theme.actionPrimary)
                .lineStyle(StrokeStyle(lineWidth: 4))
            }
        }
    }

    @ChartContentBuilder
    private func chartPointMarks() -> some ChartContent {
        ForEach(operations) { entry in
            if let date = entry.date, let weight = entry.weight {
                PointMark(
                    x: .value("Date", date),
                    y: .value("Weight", weight)
                )
                .symbolSize(viewModel.selectedEntry?.id == entry.id ? 256 : 64)
                .foregroundStyle(theme.actionPrimary)
            }
        }
    }

    @ViewBuilder
    private func ruleMarkDateLabel(date: Date) -> some View {
        let dateText = selectedSegmentTitle == TimePeriod.week.displayName
            ? date.formatted(.dateTime.day(.twoDigits).month(.abbreviated).year(.defaultDigits))
            : date.formatted(.dateTime.month(.abbreviated).year(.defaultDigits))

        Text(dateText.lowercased())
            .fontOpenSans(.subHeading2)
            .fontWeight(.semibold)
            .foregroundColor(theme.textSubheading)
            .padding(.vertical, 1.5)
    }
}

private extension Date {
    // Returns the start of the week (Sunday)
    var startOfWeek: Date? {
        let cal = Calendar.current
        let comps = cal.dateComponents([.yearForWeekOfYear, .weekOfYear], from: self)
        guard let weekStart = cal.date(from: comps) else { return nil }
        let weekday = cal.component(.weekday, from: weekStart)
        return cal.date(byAdding: .day, value: 1 - weekday, to: weekStart)
    }
}
