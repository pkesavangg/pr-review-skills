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
    @GestureState private var dragOffset: CGFloat = 0
    @State private var currentDateRange: ClosedRange<Date> = Date()...Date()

    private let weekDaysAbbr = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
    private let yearMonthsInitial = ["J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"]

    var body: some View {
        ZStack(alignment: .trailing) {
            Chart {
                // Y-axis grid lines with goal weight (no RuleMark for goalWeight!)
                ForEach(viewModel.yAxisTicksWithGoal(), id: \.self) { tick in
                    if tick != viewModel.goalWeight {
                        RuleMark(y: .value("Y Grid", tick))
                            .lineStyle(StrokeStyle(lineWidth: 1))
                            .foregroundStyle(theme.statusUtility)
                            .zIndex(-1)
                    }
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
            .chartXScale(domain: currentDateRange)
            .chartYAxis {
                AxisMarks(values: viewModel.yAxisTicksWithGoal()) { value in
                    if let doubleValue = value.as(Double.self) {
                        if doubleValue != viewModel.goalWeight {
                            AxisGridLine()
                            AxisTick()
                        }
                        if doubleValue == viewModel.goalWeight {
                            AxisValueLabel {
                                goalWeightBubbleLabel(doubleValue)
                            }
                        } else {
                            AxisValueLabel {
                                regularYAxisLabel(doubleValue)
                            }
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
                            DragGesture()
                                .updating($dragOffset) { value, state, _ in
                                    state = value.translation.width
                                }
                                .onEnded { value in
                                    let threshold: CGFloat = 50
                                    if abs(value.translation.width) > threshold {
                                        let direction: SwipeDirection = value.translation.width > 0 ? .right : .left
                                        handleSwipe(direction: direction)
                                    }
                                }
                        )
                }
            }
            .onPreferenceChange(AnnotationHeightKey.self) { viewModel.annotationHeight = $0 }
            .zIndex(1)
        }
        .onAppear {
            currentDateRange = xAxisDomain()
        }
        .onChange(of: selectedSegmentTitle) { _, _ in
            currentDateRange = xAxisDomain()
        }
    }

    private func handleSwipe(direction: SwipeDirection) {
        let calendar = Calendar.current
        var newRange: ClosedRange<Date>
        
        switch selectedSegmentTitle {
        case TimePeriod.week.displayName:
            let daysToAdd = direction == .left ? -7 : 7
            newRange = calendar.date(byAdding: .day, value: daysToAdd, to: currentDateRange.lowerBound)!...calendar.date(byAdding: .day, value: daysToAdd, to: currentDateRange.upperBound)!
            
        case TimePeriod.month.displayName:
            let monthsToAdd = direction == .left ? -1 : 1
            newRange = calendar.date(byAdding: .month, value: monthsToAdd, to: currentDateRange.lowerBound)!...calendar.date(byAdding: .month, value: monthsToAdd, to: currentDateRange.upperBound)!
            
        case TimePeriod.year.displayName:
            let yearsToAdd = direction == .left ? -1 : 1
            newRange = calendar.date(byAdding: .year, value: yearsToAdd, to: currentDateRange.lowerBound)!...calendar.date(byAdding: .year, value: yearsToAdd, to: currentDateRange.upperBound)!
            
        default:
            newRange = currentDateRange
        }
        
        withAnimation(.easeInOut(duration: 0.3)) {
            currentDateRange = newRange
        }
    }

    @ViewBuilder
    private func goalWeightBubbleLabel(_ value: Double) -> some View {
        Text("\(Int(value))")
            .fontWeight(.bold)
            .fontOpenSans(.body3)
            .foregroundColor(.white)
            .padding(.horizontal, 5)
            .padding(.vertical, 1)
            .background(Capsule().fill(theme.statusSuccess))
            .background(
                GeometryReader { bubbleGeo in
                    Color.clear
                        .preference(key: AnnotationHeightKey.self, value: bubbleGeo.size.height)
                }
            )
            .zIndex(100)
    }

    @ViewBuilder
    private func regularYAxisLabel(_ value: Double) -> some View {
        Text("\(Int(value))")
            .fontOpenSans(.subHeading2)
            .foregroundColor(theme.textSubheading)
            .zIndex(1)
    }

    // MARK: - X Axis Helpers

    private func xAxisDomain() -> ClosedRange<Date> {
        switch selectedSegmentTitle {
        case TimePeriod.week.displayName:
            guard let start = weekStartDate() else { return Date()...Date() }
            let end = Calendar.current.date(byAdding: .day, value: 6, to: start)!
            return start...end
        case TimePeriod.year.displayName:
            guard let start = yearStartDate() else { return Date()...Date() }
            let end = Calendar.current.date(byAdding: .month, value: 11, to: start)!
            return start...end
        case TimePeriod.month.displayName:
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
        let referenceDate = operations.last?.date ?? Date()
        return referenceDate.startOfWeek
    }

    private func yearStartDate() -> Date? {
        let referenceDate = operations.last?.date ?? Date()
        let cal = Calendar.current
        let comps = cal.dateComponents([.year], from: referenceDate)
        return cal.date(from: comps)
    }

    private func monthStartDate() -> Date? {
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
        let cal = Calendar.current
        guard let range = cal.range(of: .day, in: .month, for: start) else { return [] }
        let daysInMonth = range.count

        let gridCount = max(4, min(daysInMonth, 6))
        let step = Double(daysInMonth - 1) / Double(gridCount - 1)

        var labels: [Date] = []
        for i in 0..<gridCount {
            let day = 1 + Int(round(step * Double(i)))
            let safeDay = min(day, daysInMonth)
            if let labelDate = cal.date(bySetting: .day, value: safeDay, of: start) {
                labels.append(labelDate)
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
}

private extension Date {
    var startOfWeek: Date? {
        let cal = Calendar.current
        let comps = cal.dateComponents([.yearForWeekOfYear, .weekOfYear], from: self)
        guard let weekStart = cal.date(from: comps) else { return nil }
        let weekday = cal.component(.weekday, from: weekStart)
        return cal.date(byAdding: .day, value: 1 - weekday, to: weekStart)
    }
}
