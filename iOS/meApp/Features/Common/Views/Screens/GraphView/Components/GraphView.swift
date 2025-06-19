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

    var continuousOperations: [BathScaleOperationDTO] {
        viewModel.continuousOperations(operations: operations, selectedPeriod: selectedPeriod)
    }

    var body: some View {
        HStack(spacing: 0) {
            // The swipable chart pages
            TabView(selection: $selectedPage) {
                ForEach(Array(periodPages.enumerated()), id: \.offset) { idx, opsForPage in
                    ConnectedGraphPageView(
                        operations: continuousOperations,
                        currentPageOperations: opsForPage,
                        selectedPeriod: selectedPeriod,
                        selectedWeight: $selectedWeight,
                        pageIndex: idx,
                        totalPages: periodPages.count
                    )
                    //.padding(.horizontal, 10)
                    .tag(idx)
                }
            }
            .tabViewStyle(PageTabViewStyle(indexDisplayMode: .never))
            .animation(.easeInOut(duration: 0.3), value: selectedPage)
            .indexViewStyle(.page(backgroundDisplayMode: .never))
            
            

            // Chart for only y-axis labels (no grid, no marks)
            Chart {
                // No marks!
            }
            .chartYScale(domain: 175...190)
            .frame(width: 50) // Adjust width as needed
            .chartYAxis {
                AxisMarks(values: viewModel.yAxisTicksWithGoal()) { value in
                    if let doubleValue = value.as(Double.self) {
                        if doubleValue == viewModel.goalWeight {
                            AxisValueLabel {
                                goalWeightBubbleLabel(doubleValue)
                            }
                        } else {
                            AxisValueLabel()
                        }
                    }
                }
            }
            .chartXAxis(.hidden) // Hide x-axis
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


import SwiftUI
import Charts

struct ConnectedGraphPageView: View {
    let operations: [BathScaleOperationDTO] // All operations for continuous line
    let currentPageOperations: [BathScaleOperationDTO] // Current page operations for domain
    let selectedPeriod: TimePeriod
    @Binding var selectedWeight: Double?
    let pageIndex: Int
    let totalPages: Int
    
    @StateObject private var viewModel = GraphViewModel()
    @Environment(\.appTheme) private var theme
    
    var paddedOperations: [BathScaleOperationDTO] {
        viewModel.paddedOperations(for: selectedPeriod, operations: currentPageOperations)
    }
    
    // Use current page domain for UI elements (grid, labels, etc.)
    var currentPageDomain: ClosedRange<Date> {
        viewModel.xAxisDomain(for: selectedPeriod, operations: currentPageOperations)
    }
    
    // Get extended domain for continuous line rendering
    var extendedDomain: ClosedRange<Date> {
        let paddingInterval: TimeInterval
        switch selectedPeriod {
        case .week:
            paddingInterval = 7 * 24 * 3600 // 1 week
        case .month:
            paddingInterval = 30 * 24 * 3600 // ~1 month
        case .year:
            paddingInterval = 365 * 24 * 3600 // ~1 year
        case .total:
            paddingInterval = 0
        }
        
        let extendedStart = currentPageDomain.lowerBound.addingTimeInterval(-paddingInterval)
        let extendedEnd = currentPageDomain.upperBound.addingTimeInterval(paddingInterval)
        
        return extendedStart...extendedEnd
    }
    
    // Filter operations that fall within the extended domain for continuous line
    var continuousLineOperations: [BathScaleOperationDTO] {
        operations.filter { operation in
            guard let date = operation.date else { return false }
            return extendedDomain.contains(date)
        }.sorted { ($0.date ?? .distantPast) < ($1.date ?? .distantPast) }
    }
    
    var body: some View {
        VStack(spacing: 0) {
            if currentPageOperations.isEmpty {
                VStack {
                    Spacer()
                    Text("You haven't added any entries this week.")
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
                        // Grid lines (using current page domain)
                        ForEach(viewModel.yAxisTicksWithGoal(), id: \.self) { tick in
                            if tick != viewModel.goalWeight {
                                RuleMark(y: .value("Y Grid", tick))
                                    .lineStyle(StrokeStyle(lineWidth: 1))
                                    .foregroundStyle(theme.statusUtility)
                                    .zIndex(-1)
                            }
                        }
                        
                        let gridPositions = viewModel.xAxisLabels(for: selectedPeriod, operations: currentPageOperations)
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
                        
                        // Continuous line chart (using extended domain)
                        chartContinuousLineMarks()
                        
                        // Points for current page only
                        chartCurrentPagePointMarks()
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
                    .chartXScale(domain: currentPageDomain)
                    // HIDE Y AXIS LABELS: only show grid lines and ticks, but NOT labels
                    .chartYAxis {
                        AxisMarks(values: viewModel.yAxisTicksWithGoal()) { value in
                            if let doubleValue = value.as(Double.self) {
                                if doubleValue != viewModel.goalWeight {
                                    AxisGridLine()
                                    AxisTick()
                                    // AxisValueLabel { EmptyView() } // Not needed, just omit
                                }
                                if doubleValue == viewModel.goalWeight {
                                    // AxisValueLabel { EmptyView() } // Not needed, just omit
                                }
                            }
                        }
                    }
                    .chartXAxis {
                        AxisMarks(values: viewModel.xAxisLabels(for: selectedPeriod, operations: currentPageOperations)) { value in
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
    private func chartContinuousLineMarks() -> some ChartContent {
        ForEach(continuousLineOperations) { entry in
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
    private func chartCurrentPagePointMarks() -> some ChartContent {
        ForEach(currentPageOperations) { entry in
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

// Safe subscript for arrays
extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
