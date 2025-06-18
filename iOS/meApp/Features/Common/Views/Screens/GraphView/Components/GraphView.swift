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
    let selectedPeriod: TimePeriod
    @Binding var selectedWeight: Double?
    @Binding var selectedPage: Int
    @Environment(\.appTheme) private var theme
    @StateObject private var viewModel = GraphViewModel()

    var periodPages: [[BathScaleOperationDTO]] {
        viewModel.periodPages(operations: operations, selectedPeriod: selectedPeriod)
    }

    var body: some View {
        TabView(selection: $selectedPage) {
            ForEach(Array(periodPages.enumerated()), id: \.offset) { idx, opsForPage in
                SingleGraphPageView(
                    operations: opsForPage,
                    selectedPeriod: selectedPeriod,
                    selectedWeight: $selectedWeight
                )
                .padding(.horizontal, 10)
                .tag(idx)
            }
        }
        .tabViewStyle(PageTabViewStyle(indexDisplayMode: .never))
        .animation(.easeInOut, value: selectedPage)
        .indexViewStyle(.page(backgroundDisplayMode: .never))
    }
}


import SwiftUI
import Charts

struct SingleGraphPageView: View {
    let operations: [BathScaleOperationDTO]
    let selectedPeriod: TimePeriod
    @Binding var selectedWeight: Double?
    
    @StateObject private var viewModel = GraphViewModel()
    @Environment(\.appTheme) private var theme
    
    var paddedOperations: [BathScaleOperationDTO] {
        viewModel.paddedOperations(for: selectedPeriod, operations: operations)
    }
    
    var body: some View {
        VStack(spacing: 0) {
            if operations.isEmpty {
                VStack {
                    Spacer()
                    Text("You haven’t added any entries this week.")
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
                        ForEach(viewModel.yAxisTicksWithGoal(), id: \.self) { tick in
                            if tick != viewModel.goalWeight {
                                RuleMark(y: .value("Y Grid", tick))
                                    .lineStyle(StrokeStyle(lineWidth: 1))
                                    .foregroundStyle(theme.statusUtility)
                                    .zIndex(-1)
                            }
                        }
                        let gridPositions = viewModel.xAxisLabels(for: selectedPeriod, operations: operations)
                        ForEach(Array(gridPositions.enumerated()), id: \.element) { idx, position in
                            let isFirst = idx == 0
                            let isLast = idx == gridPositions.count - 1
                            RuleMark(x: .value("X Grid", position))
                                .lineStyle(
                                    StrokeStyle(
                                        lineWidth: 1,
                                        dash: (isFirst || isLast) ? [] : [3, 3]
                                    )
                                )
                                .foregroundStyle(theme.statusUtility)
                                .zIndex(-1)
                        }
                        chartLineMarks()
                        chartPointMarks()
                    }
                    .chartOverlay { proxy in
                        GeometryReader { _ in
                            Color.clear
                                .contentShape(Rectangle())
                                .onTapGesture { location in
                                    if let (entry, _) = viewModel.getSelectedEntry(
                                        at: location,
                                        proxy: proxy,
                                        operations: paddedOperations
                                    ) {
                                        viewModel.selectedEntry = entry
                                        selectedWeight = entry.weight
                                    }
                                }
                        }
                    }
                    .chartYScale(domain: 175...190)
                    .chartXScale(domain: viewModel.xAxisDomain(for: selectedPeriod, operations: operations))
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
                        AxisMarks(values: viewModel.xAxisLabels(for: selectedPeriod, operations: operations)) { value in
                            AxisGridLine()
                            AxisTick()
                            AxisValueLabel {
                                if let date = value.as(Date.self),
                                   let labelString = viewModel.xLabelString(for: date, period: selectedPeriod) {
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
                                .onAppear { viewModel.chartHeight = geo.size.height }
                                .onChange(of: geo.size.height) {
                                    viewModel.chartHeight = geo.size.height
                                }
                        }
                    )
                    .onPreferenceChange(AnnotationHeightKey.self) { viewModel.annotationHeight = $0 }
                    .zIndex(1)
                }
            }
        }
    }
    
    @ChartContentBuilder
    private func chartLineMarks() -> some ChartContent {
        ForEach(paddedOperations) { entry in
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
        ForEach(paddedOperations) { entry in
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

}

// Safe subscript for arrays
extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
