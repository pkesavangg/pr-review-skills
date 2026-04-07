import Charts
import SwiftUI

extension BaseGraphView {

    @ViewBuilder
    func conditionalModifiers<Content: View>(
        _ content: Content,
        xAxisLabels: [Date: String]
    ) -> some View {
        if viewModel.hasXAxis {
            scrollableChartModifiers(content, xAxisLabels: xAxisLabels)
        } else {
            nonScrollableChartModifiers(content)
        }
    }

    @ViewBuilder
    // swiftlint:disable:next function_body_length cyclomatic_complexity
    func scrollableChartModifiers<Content: View>(_ content: Content, xAxisLabels: [Date: String]) -> some View {
        babyScrollDomainCap(
            conditionalEmptyDomain(
                content
                    .chartXVisibleDomain(length: viewModel.visibleDomainLength)
            )
        )
        .chartScrollableAxes(.horizontal)
        .chartScrollPosition(x: Binding(
            get: {
                viewModel.scrollPosition
            },
            set: { (newPosition: Date?) in
                guard let newPosition else { return }
                viewModel.handleScrollPositionChange(newPosition)
            }
        ))
        .chartXAxis {
            let allTicks = viewModel.xAxisValues
            let nonLastTicks = Array(allTicks.dropLast())
            let gridTicks: [Date] = {
                guard viewModel.timePeriod == .month, !nonLastTicks.isEmpty else {
                    return nonLastTicks
                }
                let calendar = Calendar.current
                let sortedTicks = nonLastTicks.sorted()
                guard let firstTick = sortedTicks.first,
                      let lastTick = sortedTicks.last else {
                    return nonLastTicks
                }

                var monthStartTicks: [Date] = []
                var currentMonthStart = calendar.dateInterval(of: .month, for: firstTick)?.start ?? firstTick
                while currentMonthStart <= lastTick {
                    let monthStartNoon = calendar.date(bySettingHour: 12, minute: 0, second: 0, of: currentMonthStart) ?? currentMonthStart
                    monthStartTicks.append(monthStartNoon)
                    guard let next = calendar.date(byAdding: .month, value: 1, to: currentMonthStart) else { break }
                    currentMonthStart = next
                }

                let combined = nonLastTicks + monthStartTicks
                var uniqueByDay: [Date] = []
                var seenDays: Set<Date> = []
                for tick in combined.sorted() {
                    let day = calendar.startOfDay(for: tick)
                    if seenDays.insert(day).inserted {
                        uniqueByDay.append(tick)
                    }
                }
                return uniqueByDay
            }()
            let adjustedLabelTicks: [Date] = {
                if viewModel.timePeriod == .year {
                    return nonLastTicks
                }
                return allTicks
            }()
            let renderedGridTicks: [Date] = {
                if dashboardStore.selectedBabyProfile != nil && viewModel.hasXAxis {
                    return Array(gridTicks.dropLast())
                }
                return gridTicks
            }()

            AxisMarks(values: renderedGridTicks) { value in
                if let date = value.as(Date.self), viewModel.shouldShowSolidLine(for: date) {
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 1, dash: []))
                        .foregroundStyle(theme.statusIconSecondaryDisabled)

                    if viewModel.timePeriod == .month {
                        let calendar = Calendar.current
                        let components = calendar.dateComponents([.day, .weekday], from: date)
                        let isMonthStartSunday = components.day == 1 && components.weekday == 1
                        if isMonthStartSunday {
                            AxisTick(stroke: StrokeStyle(lineWidth: 1, dash: []))
                                .foregroundStyle(theme.statusIconSecondaryDisabled)
                        } else {
                            AxisTick().foregroundStyle(.clear)
                        }
                    } else {
                        AxisTick(stroke: StrokeStyle(lineWidth: 1, dash: []))
                            .foregroundStyle(theme.statusIconSecondaryDisabled)
                    }
                } else {
                    AxisGridLine()
                    AxisTick()
                }
            }

            AxisMarks(values: adjustedLabelTicks) { value in
                if viewModel.timePeriod == .month {
                    AxisGridLine().foregroundStyle(.clear)
                    AxisTick().foregroundStyle(.clear)
                }
                AxisValueLabel {
                    if let date = value.as(Date.self),
                       let labelString = xAxisLabels[date] ?? viewModel.formatXAxisLabel(for: date) {
                        if viewModel.timePeriod == .month {
                            Text(labelString)
                                .font(.caption)
                                .foregroundStyle(theme.textSubheading)
                                .fixedSize(horizontal: true, vertical: false)
                                .padding(.horizontal, 2)
                                .background(theme.textInverse)
                        } else {
                            Text(labelString)
                                .font(.caption)
                                .foregroundStyle(theme.textSubheading)
                        }
                    }
                }
            }
        }
        .chartPlotStyle { plot in
            if dashboardStore.selectedBabyProfile != nil {
                plot.overlay {
                    if viewModel.hasXAxis {
                        SnapshotChartPlotBorderView(
                            color: theme.statusIconSecondaryDisabled,
                            yDomain: viewModel.yAxisDomain,
                            yTicks: viewModel.yAxisTicks,
                            showHorizontalGridLines: false,
                            visibleHorizontalTicks: BaseGraphViewCacheSupport.boundaryYAxisTicks(from: viewModel.yAxisTicks)
                        )
                    }
                }
            } else if viewModel.isAtLeftBoundary {
                plot.padding(.leading, .spacingXS)
            } else {
                plot
            }
        }
        .chartXSelection(value: Binding(
            get: { localSelectedXValue },
            set: { newValue in
                if viewModel.chartOperations.isEmpty {
                    localSelectedXValue = nil
                    viewModel.clearSelection()
                    return
                }
                guard !viewModel.isScrolling else { return }
                if let selectedDate = newValue {
                    localSelectedXValue = newValue
                    viewModel.handleChartSelection(at: newValue)
                    if viewModel.showCrosshair {
                        let dateToSend = viewModel.preferredSelectedDate ?? selectedDate
                        Task {
                            await dashboardStore.chartManager.handleChartSelection(at: dateToSend)
                        }
                    } else {
                        Task {
                            await dashboardStore.chartManager.handleChartSelection(at: nil)
                        }
                    }
                }
            }
        ))
        .chartGesture { proxy in
            SpatialTapGesture()
                .onEnded { value in
                    guard !viewModel.chartOperations.isEmpty else { return }
                    guard !viewModel.isScrolling else { return }
                    if let date: Date = proxy.value(atX: value.location.x) {
                        localSelectedXValue = date
                        viewModel.handleChartSelection(at: date)
                        if viewModel.showCrosshair {
                            let dateToSend = viewModel.preferredSelectedDate ?? date
                            Task {
                                await dashboardStore.chartManager.handleChartSelection(at: dateToSend)
                            }
                        } else {
                            Task {
                                await dashboardStore.chartManager.handleChartSelection(at: nil)
                            }
                        }
                    }
                }
        }
    }

    @ViewBuilder
    // swiftlint:disable:next function_body_length
    func nonScrollableChartModifiers<Content: View>(_ content: Content) -> some View {
        content
            .chartXScale(domain: viewModel.dateRange)
            .chartXAxis {
                AxisMarks(position: .bottom) { _ in
                    AxisGridLine().foregroundStyle(.clear)
                    AxisTick().foregroundStyle(.clear)
                    AxisValueLabel {
                        Text("00")
                            .font(.caption)
                            .opacity(0)
                    }
                }
            }
            .chartXSelection(value: Binding(
                get: { localSelectedXValue },
                set: { newValue in
                    if viewModel.chartOperations.isEmpty {
                        localSelectedXValue = nil
                        viewModel.clearSelection()
                        return
                    }
                    viewModel.handleChartSelection(at: newValue)

                    if let rawDate = newValue {
                        if viewModel.showCrosshair {
                            let dateToSend = viewModel.preferredSelectedDate ?? rawDate
                            localSelectedXValue = dateToSend
                            Task {
                                await dashboardStore.chartManager.handleChartSelection(at: dateToSend)
                            }
                        } else {
                            localSelectedXValue = nil
                            Task {
                                await dashboardStore.chartManager.handleChartSelection(at: nil)
                            }
                        }
                    } else {
                        localSelectedXValue = nil
                    }
                }
            ))
            .chartGesture { proxy in
                SpatialTapGesture()
                    .onEnded { value in
                        guard !viewModel.chartOperations.isEmpty else { return }
                        if let date: Date = proxy.value(atX: value.location.x) {
                            viewModel.handleChartSelection(at: date)
                            if viewModel.showCrosshair {
                                let dateToSend = viewModel.preferredSelectedDate ?? date
                                localSelectedXValue = dateToSend
                                Task {
                                    await dashboardStore.chartManager.handleChartSelection(at: dateToSend)
                                }
                            } else {
                                localSelectedXValue = nil
                                Task {
                                    await dashboardStore.chartManager.handleChartSelection(at: nil)
                                }
                            }
                        }
                    }
            }
    }

    @ViewBuilder
    func conditionalEmptyDomain<Content: View>(_ content: Content) -> some View {
        if viewModel.hasXAxis && viewModel.chartOperations.isEmpty {
            let ticks = viewModel.xAxisValues.sorted()
            if let first = ticks.first, let last = ticks.last, first < last {
                content.chartXScale(domain: first...last)
            } else {
                content
            }
        } else {
            content
        }
    }

    @ViewBuilder
    func babyScrollDomainCap<Content: View>(_ content: Content) -> some View {
        if dashboardStore.selectedBabyProfile != nil && viewModel.hasXAxis && !viewModel.chartOperations.isEmpty {
            let operations = viewModel.chartOperations
            let dates = operations.map(\.date)
            if let minDate = dates.min(), let maxDate = dates.max() {
                let now = Date()
                let buffer: TimeInterval = {
                    switch viewModel.timePeriod {
                    case .week:
                        let weekday = Calendar.current.component(.weekday, from: now)
                        let daysToSaturday = (7 - weekday + 7) % 7
                        return TimeInterval(daysToSaturday + 1) * DashboardConstants.TimeInterval.day
                    case .month:
                        return DashboardConstants.TimeInterval.calendarWeek
                    case .year:
                        return DashboardConstants.TimeInterval.month
                    case .total:
                        return 0
                    }
                }()
                let cappedMax: Date = {
                    if viewModel.timePeriod == .year {
                        let calendar = Calendar.current
                        let yearEnd = calendar.dateInterval(of: .year, for: max(maxDate, now))?.end ?? now
                        return max(yearEnd, now.addingTimeInterval(buffer))
                    }
                    return now.addingTimeInterval(buffer)
                }()
                let domainMin: Date = {
                    if viewModel.timePeriod == .year {
                        let calendar = Calendar.current
                        return calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
                    }
                    return minDate
                }()
                content.chartXScale(domain: domainMin...max(maxDate, cappedMax))
            } else {
                content
            }
        } else {
            content
        }
    }

    @ViewBuilder
    func conditionalPreferenceChange<Content: View>(_ content: Content) -> some View {
        if viewModel.hasXAxis {
            content.onPreferenceChange(AnnotationHeightKey.self) { height in
                dashboardStore.state.graph.annotationHeight = height
            }
        } else {
            content
        }
    }

    @ViewBuilder
    func conditionalTouchModifiers<Content: View>(_ content: Content) -> some View {
        if viewModel.hasXAxis {
            content.modifier(
                ScrollDetectionModifier(
                    dashboardStore: dashboardStore,
                    selectedXValue: $localSelectedXValue
                )
            )
        } else {
            content
        }
    }

    func getChartScrollBehavior(for period: TimePeriod) -> PagedChartScrollBehavior {
        switch period {
        case .week:
            return PagedChartScrollBehavior(
                matching: DateComponents(hour: 12),
                majorAlignment: DateComponents(hour: 6, weekday: 1)
            )
        case .month:
            return PagedChartScrollBehavior(
                matching: DateComponents(hour: 12),
                majorAlignment: DateComponents(day: 31, hour: 12)
            )
        case .year:
            return PagedChartScrollBehavior(
                matching: DateComponents(day: 1, hour: 12),
                majorAlignment: DateComponents(month: 1, day: 1, hour: 12)
            )
        case .total:
            return PagedChartScrollBehavior(
                matching: DateComponents(hour: 0),
                majorAlignment: DateComponents(hour: 0)
            )
        }
    }

    @ViewBuilder
    func conditionalScrollSyncing<Content: View>(_ content: Content) -> some View {
        if viewModel.hasXAxis {
            content
                .onChange(of: dashboardStore.state.graph.xScrollPosition) { _, newPosition in
                    guard abs(newPosition.timeIntervalSince(viewModel.scrollPosition)) > 0.1 else { return }
                    viewModel.updateScrollPosition(to: newPosition)
                }
                .onChange(of: dashboardStore.state.graph.isScrolling) { _, newValue in
                    viewModel.isScrolling = newValue
                    if newValue {
                        localSelectedXValue = nil
                        viewModel.clearSelection()
                    }
                }
                .onChange(of: dashboardStore.state.graph.selectedPeriod) { _, _ in
                    localSelectedXValue = nil
                    viewModel.clearSelection()
                }
                .onChange(of: dashboardStore.state.graph.cachedYAxisDomain) { _, _ in
                    viewModel.syncYAxisFromStore()
                }
                .onChange(of: dashboardStore.state.graph.cachedYAxisTicks) { _, _ in
                    viewModel.syncYAxisFromStore()
                }
        } else {
            content
        }
    }
}
