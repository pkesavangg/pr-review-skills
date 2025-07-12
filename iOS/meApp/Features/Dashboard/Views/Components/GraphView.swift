//
//  GraphView.swift
//  meApp
//
//  Created by Lakshmi Priya on 10/06/25.
//

import SwiftUI
import Charts

struct GraphView: View {
    @ObservedObject var dashboardStore: DashboardStore
    @Environment(\.appTheme) private var theme

    // Check if there are any entries to display
    private var hasEntries: Bool {
        !dashboardStore.continuousOperations.isEmpty
    }

    // Get the appropriate empty state message
    private var emptyStateMessage: String {
        if dashboardStore.hasEntriesButNoneInCurrentPeriod {
            return DashboardStrings.noEntriesInPeriodMessage(dashboardStore.state.graph.selectedPeriod.rawValue)
        } else {
            return DashboardStrings.noEntriesMessage
        }
    }

    var body: some View {
        VStack(alignment: .leading){
            Text(dashboardStore.weightLabel)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
                    .padding(.leading, .spacingSM)
                    .padding(.vertical, .spacingXS)

            if hasEntries {
                // Show chart when there are entries
                chartView
            } else {
                // Show empty state message when there are no entries
                emptyStateView
            }
        }
        .onChange(of: dashboardStore.state.graph.selectedPeriod) { _, _ in
            // Clear crosshair and selection when time period changes
            dashboardStore.clearSelection()
        }
    }

    // MARK: - Chart View
    private var chartView: some View {
        return HStack(spacing: 0) {
            Chart {
                yAxisGridLines
                chartSeries
                crosshairContent
            }
            .chartXVisibleDomain(length: dashboardStore.visibleDomainLength(for: dashboardStore.state.graph.selectedPeriod))
            .chartScrollableAxes(.horizontal)
            .chartScrollTargetBehavior(.paging)
            .chartScrollTargetBehavior(.valueAligned(unit: dashboardStore.timeSnapUnit(for: dashboardStore.state.graph.selectedPeriod)))
            .chartYScale(domain: {
                let yAxisScale = dashboardStore.getYAxisScale()
                return yAxisScale.domain
            }())
            .chartScrollPosition(x: Binding(
                get: { dashboardStore.state.graph.xScrollPosition },
                set: { newPosition in
                    dashboardStore.handleScrollPositionChange(newPosition)
                }
            ))
            .chartForegroundStyleScale([
                DashboardStrings.weight: theme.actionPrimary,
                DashboardStrings.bmi: theme.actionSecondary,
                DashboardStrings.bodyFat: theme.actionSecondary,
                DashboardStrings.muscle: theme.actionSecondary,
                DashboardStrings.water: theme.actionSecondary,
                DashboardStrings.heartBpm: theme.actionSecondary,
                DashboardStrings.bone: theme.actionSecondary,
                DashboardStrings.visceralFat: theme.actionSecondary,
                DashboardStrings.subFat: theme.actionSecondary,
                DashboardStrings.protein: theme.actionSecondary,
                DashboardStrings.skelMuscle: theme.actionSecondary,
                DashboardStrings.bmrKcal: theme.actionSecondary,
                DashboardStrings.metAge: theme.actionSecondary
            ])
            .chartYAxis { yAxisMarks }
            .chartLegend(.hidden)
            .chartXAxis { xAxisMarks }
            .chartXSelection(value: Binding(
                get: {
                    // Only return selected value if not currently scrolling
                    dashboardStore.state.graph.isScrolling ? nil : dashboardStore.state.graph.selectedXValue
                },
                set: { newValue in
                    // Only handle selection if not scrolling
                    if !dashboardStore.state.graph.isScrolling {
                        dashboardStore.handleChartSelection(at: newValue)
                    }
                }
            ))
            .frame(height: 265)
            .frame(maxWidth: .infinity, minHeight: 240)
            .padding(.horizontal)
            .background(
                GeometryReader { geo in
                    theme.textInverse
                        .onAppear {
                            dashboardStore.state.graph.chartHeight = geo.size.height
                        }
                }
            )
            .onPreferenceChange(AnnotationHeightKey.self) { height in
                dashboardStore.state.graph.annotationHeight = height
            }
            .accessibilityLabel(Text("Weight chart"))
            .onAppear {
                dashboardStore.initializeChart()
            }
            // Simplified scroll detection - only detect when scrolling starts/ends
            .gesture(
                DragGesture(minimumDistance: 10)
                    .onChanged { _ in
                        dashboardStore.handleScrollStart()
                    }
                    .onEnded { _ in
                        dashboardStore.handleScrollEndOptimized()
                    }
            )
        }
        // Single chart refresh trigger for better performance
        .id("\(dashboardStore.state.graph.selectedPeriod.rawValue)-\(dashboardStore.currentUnit.rawValue)-\(dashboardStore.state.graph.dataChangeTrigger)")
    }

    // MARK: - Empty State View
    private var emptyStateView: some View {
        VStack(spacing: .spacingMD) {
            Spacer()

            Text(emptyStateMessage)
                .fontOpenSans(.heading5)
                .foregroundColor(theme.textHeading)
                .fontWeight(.bold)
                .multilineTextAlignment(.center)
                .padding(.horizontal, .spacingLG)

            Spacer()
        }
        .frame(height: 265)
        .frame(maxWidth: .infinity, minHeight: 240)
        .padding(.horizontal)
        .background(theme.textInverse)
    }

    // MARK: - Chart Content Builders
    @ChartContentBuilder
    private var yAxisGridLines: some ChartContent {
        let yAxisScale = dashboardStore.getYAxisScale()

        ForEach(yAxisScale.ticks, id: \.self) { tick in
            if abs(tick - dashboardStore.goalWeightForDisplay) > 0.01 {
                RuleMark(y: .value("YGrid", tick))
                    .lineStyle(StrokeStyle(lineWidth: 1))
                    .foregroundStyle(theme.statusUtilityPrimary.opacity(0.3))
                    .zIndex(-1)
            }
        }
    }

    @ChartContentBuilder
    private var chartSeries: some ChartContent {
        let seriesData = dashboardStore.chartSeriesData

        ForEach(seriesData) { series in

            LineMark(
                x: .value("Date", series.date),
                y: .value(series.series, series.value)
            )
            .foregroundStyle(by: .value("Series", series.series))
            .interpolationMethod(.catmullRom)
            .lineStyle(StrokeStyle(lineWidth: 3))

            PointMark(
                x: .value("Date", series.date),
                y: .value(series.series, series.value)
            )
            .symbolSize(series.date == dashboardStore.state.graph.selectedPoint?.date ? 200 : 64)
            .foregroundStyle(by: .value("Series", series.series))
        }
    }

    @ChartContentBuilder
    private var crosshairContent: some ChartContent {
        if let selectedPoint = dashboardStore.state.graph.selectedPoint, dashboardStore.state.graph.showCrosshair {
            // Dotted vertical line
            RuleMark(
                x: .value("Date", selectedPoint.date)
            )
            .zIndex(-100)
            .foregroundStyle(theme.actionSecondary)
            .lineStyle(StrokeStyle(lineWidth: 2))
        }
    }

    // MARK: - Axis Marks Builders
    private var yAxisMarks: some AxisContent {
        let yAxisScale = dashboardStore.getYAxisScale()

        return AxisMarks(values: yAxisScale.ticks) { value in
            if let doubleValue = value.as(Double.self) {
                if abs(doubleValue - dashboardStore.goalWeightForDisplay) < 0.01 {
                    AxisValueLabel {
                        goalWeightBubbleLabel(doubleValue)
                    }
                } else {
                    AxisValueLabel {
                        Text(dashboardStore.formatYAxisTickLabel(doubleValue))
                            .font(.body)
                            .fontWeight(.medium)
                            .foregroundColor(theme.textSubheading)
                    }
                }
            }
        }
    }

    private var xAxisMarks: some AxisContent {
        AxisMarks(values: dashboardStore.xAxisValuesWithBuffer(for: dashboardStore.state.graph.selectedPeriod)) { value in
            AxisGridLine()
            AxisTick()
            AxisValueLabel {
                if let date = value.as(Date.self),
                   let labelString = dashboardStore.xLabelString(for: date, period: dashboardStore.state.graph.selectedPeriod) {
                    Text(labelString)
                        .font(.caption)
                        .foregroundColor(.gray)
                }
            }
        }
    }

    // MARK: - Axis Label Helpers
    @ViewBuilder
    private func goalWeightBubbleLabel(_ value: Double) -> some View {
        Text(dashboardStore.formatYAxisTickLabel(value))
            .fontWeight(.bold)
            .font(.body)
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
}

#Preview {
    GraphView(dashboardStore: DashboardStore())
        .frame(height: 240)
        .padding()
}
