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
        
    var body: some View {
        HStack(spacing: 0) {
            Chart {
                // Grid lines
                ForEach(dashboardStore.getYAxisTicksForAllData(), id: \.self) { tick in
                    if abs(tick - dashboardStore.goalWeightForDisplay) > 0.01 {
                        RuleMark(y: .value("YGrid", tick))
                            .lineStyle(StrokeStyle(lineWidth: 1))
                            .foregroundStyle(theme.statusUtilityPrimary.opacity(0.3))
                            .zIndex(-1)
                    }
                }

                // Render all series data
                ForEach(dashboardStore.chartSeriesData) { series in
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
                    .symbolSize(40)
                    .foregroundStyle(by: .value("Series", series.series))
                }
            }
            .chartXVisibleDomain(length: dashboardStore.visibleDomainLength(for: dashboardStore.selectedPeriod))
            .chartScrollableAxes(.horizontal)
            .chartScrollTargetBehavior(.valueAligned(unit: dashboardStore.timeSnapUnit(for: dashboardStore.selectedPeriod)))
            .chartYScale(domain: dashboardStore.getYScaleDomainForAllData())
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
            .chartYAxis {
                AxisMarks(values: dashboardStore.getYAxisTicksForAllData()) { value in
                    if let doubleValue = value.as(Double.self) {
                        if abs(doubleValue - dashboardStore.goalWeightForDisplay) < 0.01 {
                            AxisValueLabel {
                                goalWeightBubbleLabel(doubleValue)
                            }
                        } else {
                            AxisValueLabel {
                                Text(dashboardStore.formatWeightDisplayText(doubleValue))
                                    .font(.body)
                                    .fontWeight(.medium)
                                    .foregroundColor(theme.textSubheading)
                            }
                        }
                    }
                }
            }
            .chartLegend(.hidden)
            .chartXAxis {
                AxisMarks(values: dashboardStore.xAxisValuesWithBuffer(for: dashboardStore.selectedPeriod)) { value in
                    AxisGridLine()
                    AxisTick()
                    AxisValueLabel {
                        if let date = value.as(Date.self),
                           let labelString = dashboardStore.xLabelString(for: date, period: dashboardStore.selectedPeriod) {
                            Text(labelString)
                                .font(.caption)
                                .foregroundColor(.gray)
                        }
                    }
                }
            }
            .frame(height: 265)
            .frame(maxWidth: .infinity, minHeight: 240)
            .padding(.horizontal)
            .background(
                GeometryReader { geo in
                    theme.textInverse
                        .onAppear { dashboardStore.chartHeight = geo.size.height }
                }
            )
            .onPreferenceChange(AnnotationHeightKey.self) { dashboardStore.annotationHeight = $0 }
            .accessibilityLabel(Text("Weight chart"))
        }
    }

    // MARK: - Axis Label Helpers

    @ViewBuilder
    private func goalWeightBubbleLabel(_ value: Double) -> some View {
        Text(dashboardStore.formatWeightDisplayText(value))
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
