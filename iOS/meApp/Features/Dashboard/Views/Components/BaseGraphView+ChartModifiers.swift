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
            let gridTicks = viewModel.gridTicks
            let adjustedLabelTicks = viewModel.adjustedLabelTicks
            AxisMarks(values: gridTicks) { value in
                if let date = value.as(Date.self), viewModel.shouldShowSolidLine(for: date) {
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 1, dash: []))
                        .foregroundStyle(theme.statusIconSecondaryDisabled)

                    if viewModel.timePeriod == .month {
                        let calendar = Calendar.current
                        let isMonthStart = calendar.component(.day, from: date) == 1
                        if isMonthStart {
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
                            visibleHorizontalTicks: BaseGraphViewCacheSupport.boundaryYAxisTicks(from: viewModel.yAxisTicks),
                            showTrailingBorder: viewModel.timePeriod != .month
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
                let cappedMax: Date = {
                    let calendar = Calendar.current
                    switch viewModel.timePeriod {
                    case .week:
                        let weekday = calendar.component(.weekday, from: now)
                        let daysToSaturday = (7 - weekday + 7) % 7
                        let buffer = TimeInterval(daysToSaturday + 1) * DashboardConstants.TimeInterval.day
                        return now.addingTimeInterval(buffer)
                    case .month:
                        if let interval = calendar.dateInterval(of: .month, for: now) {
                            // Extend to noon on the 1st of the next month so the month-boundary
                            // solid grid line (day == 1) is not clipped by the domain.
                            return calendar.date(bySettingHour: 12, minute: 0, second: 0, of: interval.end) ?? interval.end
                        }
                        return now.addingTimeInterval(DashboardConstants.TimeInterval.calendarWeek)
                    case .year:
                        let yearEnd = calendar.dateInterval(of: .year, for: max(maxDate, now))?.end ?? now
                        return max(yearEnd, now.addingTimeInterval(DashboardConstants.TimeInterval.month))
                    case .total:
                        return now
                    }
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
                .onChange(of: dashboardYAxisCacheSignature) { _, _ in
                    viewModel.syncYAxisFromStore()
                }
        } else {
            content
        }
    }
}
