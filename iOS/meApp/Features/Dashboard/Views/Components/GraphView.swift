//
//  GraphView.swift
//  meApp
//
//  Created by Lakshmi Priya on 10/06/25.
//

//
//  GraphView.swift
//  meApp
//
//  Created by Lakshmi Priya on 10/06/25.
//

import SwiftUI
import Charts

struct GraphView: View {
    @ObservedObject var graphStore: GraphStore
    @Environment(\.appTheme) private var theme

    let yAxisContainerWidth: CGFloat = 30

    var body: some View {
        HStack(spacing: 0) {
            // MAIN CHART (scrollable, Health-style)
            Chart {
                // Grid lines for each tick except goal
                ForEach(graphStore.yAxisTicksWithGoal(), id: \.self) { tick in
                    if tick != graphStore.goalWeight {
                        RuleMark(y: .value("YGrid", tick))
                            .lineStyle(StrokeStyle(lineWidth: 1))
                            .foregroundStyle(theme.statusUtilityPrimary)
                            .zIndex(-1)
                    }
                }
                // The continuous line
                ForEach(graphStore.continuousOperations) { entry in
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
                // Points - highlight selected
                ForEach(graphStore.operations) { entry in
                    if let date = entry.date, let weight = entry.weight {
                        PointMark(
                            x: .value("Date", date),
                            y: .value("Weight", weight)
                        )
                        .symbolSize(graphStore.selectedEntry?.id == entry.id ? 180 : 64)
                        .foregroundStyle(graphStore.selectedEntry?.id == entry.id ? .red : theme.actionPrimary)
                        .annotation(position: .top, spacing: 8) {
                            if graphStore.selectedEntry?.id == entry.id {
                                Text("\(weight, specifier: "%.1f")")
                                    .font(.caption)
                                    .padding(4)
                            }
                        }
                    }
                }
            }
            .chartXVisibleDomain(length: graphStore.visibleDomainLength(for: graphStore.selectedPeriod))
            .chartScrollableAxes(.horizontal)
            .chartScrollPosition(x: $graphStore.xScrollPosition)
            .chartScrollTargetBehavior(.valueAligned(unit: graphStore.timeSnapUnit(for: graphStore.selectedPeriod)))
            .chartYScale(domain: graphStore.yAxisTicksWithGoal().min()!...graphStore.yAxisTicksWithGoal().max()!)
            .chartYAxis {
                AxisMarks(values: graphStore.yAxisTicksWithGoal()) { value in
                    if let doubleValue = value.as(Double.self) {
                        if doubleValue == graphStore.goalWeight {
                            AxisValueLabel {
                                goalWeightBubbleLabel(doubleValue)
                                    .offset(x: -3)
                            }
                        } else {
                            AxisValueLabel {
                                Text("\(Int(doubleValue))")
                                    .fontOpenSans(.body3)
                                    .fontWeight(.bold)
                            }
                            .offset(x: -3)
                        }
                    }
                }
            }
            .chartXAxis {
                AxisMarks(values: graphStore.xAxisValues(for: graphStore.selectedPeriod)) { value in
                    AxisGridLine()
                    AxisTick()
                    AxisValueLabel {
                        if let date = value.as(Date.self),
                           let labelString = graphStore.xLabelString(for: date, period: graphStore.selectedPeriod) {
                            Text(labelString)
                                .fontOpenSans(.subHeading2)
                                .foregroundColor(.gray)
                        }
                    }
                }
            }
            .background(
                GeometryReader { geo in
                    theme.textInverse
                        .onAppear { graphStore.chartHeight = geo.size.height }
                        .onChange(of: geo.size.height) { graphStore.chartHeight = geo.size.height }
                }
            )
            .onPreferenceChange(AnnotationHeightKey.self) { graphStore.annotationHeight = $0 }
            .frame(minHeight: 265)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .accessibilityLabel(Text("Weight chart"))
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
}
