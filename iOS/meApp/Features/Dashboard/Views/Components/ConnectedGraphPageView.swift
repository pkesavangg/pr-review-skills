//
//  ConnectedGraphPageView.swift
//  meApp
//
//  Created by Lakshmi Priya on 20/06/25.
//

import SwiftUI
import Charts

struct ConnectedGraphPageView: View {
    @ObservedObject var graphStore: GraphStore
    let currentPageOperations: [BathScaleOperationDTO]
    let pageIndex: Int
    let totalPages: Int
    
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: 0) {
            if currentPageOperations.isEmpty {
                VStack {
                    Spacer()
                    Text(GraphViewStrings.noEntriesThisWeek)
                        .fontOpenSans(.body2)
                        .fontWeight(.bold)
                        .foregroundColor(theme.textHeading)
                        .multilineTextAlignment(.center)
                        .padding()
                    Spacer()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ZStack(alignment: .trailing) {
                    Chart {
                        
                        ForEach(graphStore.yAxisTicksWithGoal(), id: \.self) { tick in
                            if tick != graphStore.goalWeight {
                                RuleMark(y: .value(GraphViewStrings.yGrid, tick))
                                    .lineStyle(StrokeStyle(lineWidth: 1))
                                    .foregroundStyle(theme.statusUtilityPrimary)
                                    .zIndex(-1)
                            }
                        }

                        // Draw X grid lines
                        let gridPositions = graphStore.xAxisLabels(for: graphStore.selectedPeriod, operations: currentPageOperations)
                        ForEach(Array(gridPositions.enumerated()), id: \.element) { idx, position in
                            let isFirst = position == graphStore.getFirstDateInAllOps(graphStore.operations)
                            let isLast = position == graphStore.getLastDateInAllOps(graphStore.operations)
                            RuleMark(x: .value(GraphViewStrings.xGrid, position))
                                .lineStyle(
                                    StrokeStyle(
                                        lineWidth: 1,
                                        dash: (isFirst || isLast) ? [] : [3, 3]
                                    )
                                )
                                .foregroundStyle(theme.statusUtilityPrimary)
                                .zIndex(-1)
                        }

                        chartContinuousLineMarks()
                        chartCurrentPagePointMarks()
                    }
                    .chartOverlay { proxy in
                        GeometryReader { _ in
                            Color.clear
                                .contentShape(Rectangle())
                                .onTapGesture { location in
                                    if let (entry, _) = graphStore.getSelectedEntry(
                                        at: location,
                                        proxy: proxy,
                                        operations: graphStore.getPaddedOperations(for: graphStore.selectedPeriod, operations: currentPageOperations)
                                    ) {
                                        graphStore.selectEntry(entry)
                                    }
                                }
                        }
                    }
                    .chartYScale(domain: 175...190)
                    .chartXScale(domain: graphStore.getCurrentPageDomain(for: graphStore.selectedPeriod, operations: currentPageOperations))
                    .chartYAxis {
                        AxisMarks(values: graphStore.yAxisTicksWithGoal()) { value in
                            if let doubleValue = value.as(Double.self) {
                                if doubleValue != graphStore.goalWeight {
                                    AxisGridLine()
                                    AxisTick()
                                }
                            }
                        }
                    }
                    .chartXAxis {
                        AxisMarks(values: graphStore.xAxisLabels(for: graphStore.selectedPeriod, operations: currentPageOperations)) { value in
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
                            .foregroundStyle(.gray)
                        }
                    }
                    .background(
                        GeometryReader { geo in
                            theme.textInverse
                                .onAppear { graphStore.chartHeight = geo.size.height }
                                .onChange(of: geo.size.height) {
                                    graphStore.chartHeight = geo.size.height
                                }
                        }
                    )
                    .onPreferenceChange(AnnotationHeightKey.self) { graphStore.annotationHeight = $0 }
                    .zIndex(1)
                }
            }
        }
    }

    @ChartContentBuilder
    private func chartContinuousLineMarks() -> some ChartContent {
        ForEach(graphStore.getContinuousLineOperations(
            allOperations: graphStore.continuousOperations,
            currentPageOperations: currentPageOperations,
            period: graphStore.selectedPeriod
        )) { entry in
            if let date = entry.date, let weight = entry.weight {
                LineMark(
                    x: .value(CommonStrings.date, date),
                    y: .value(CommonStrings.weight, weight)
                )
                .interpolationMethod(.catmullRom)
                .foregroundStyle(theme.actionPrimary)
                .lineStyle(StrokeStyle(lineWidth: 4))
            }
        }
    }

    @ChartContentBuilder
    private func chartCurrentPagePointMarks() -> some ChartContent {
        ForEach(currentPageOperations) { entry in
            if let date = entry.date, let weight = entry.weight {
                PointMark(
                    x: .value(CommonStrings.date, date),
                    y: .value(CommonStrings.weight, weight)
                )
                .symbolSize(graphStore.selectedEntry?.id == entry.id ? 256 : 64)
                .foregroundStyle(theme.actionPrimary)
            }
        }
    }
}
