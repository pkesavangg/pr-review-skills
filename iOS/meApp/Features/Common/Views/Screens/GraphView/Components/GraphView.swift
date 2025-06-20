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
    
    let yAxisContainerWidth: CGFloat = 30 // Width for Y-axis container
    
    var body: some View {
        HStack(spacing: 0) {
            // The swipable chart pages
            TabView(selection: Binding(
                get: { graphStore.selectedPage },
                set: { graphStore.selectedPage = $0 }
            )) {
                ForEach(Array(graphStore.periodPages.enumerated()), id: \.offset) { idx, opsForPage in
                    ConnectedGraphPageView(
                        graphStore: graphStore,
                        currentPageOperations: opsForPage,
                        pageIndex: idx,
                        totalPages: graphStore.periodPages.count
                    )
                    .padding(.vertical, 0)
                    .padding(.horizontal, 0)
                    .tag(idx)
                }
            }
            .tabViewStyle(PageTabViewStyle(indexDisplayMode: .never))
            .animation(.easeInOut(duration: 0.3), value: graphStore.selectedPage)
            .indexViewStyle(.page(backgroundDisplayMode: .never))
            .clipped()

            Chart {
                ForEach(graphStore.yAxisTicksWithGoal(), id: \.self) { tick in
                    RuleMark(y: .value(GraphViewStrings.yGrid, tick))
                        .foregroundStyle(.clear)
                }
            }
            .chartYScale(domain: {
                let ticks = graphStore.yAxisTicksWithGoal()
                guard let minTick = ticks.min(), let maxTick = ticks.max() else {
                    return 175...190 
                }
                return Int(minTick)...Int(maxTick)
            }())
            .chartXAxis(.hidden)
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
                                Text(value.as(Int.self)?.formatted() ?? "")
                                    .fontOpenSans(.body3)
                                    .fontWeight(.bold)
                            }
                            .offset(x: -3)
                            
                        }
                    }
                }
            }
            .frame(width: yAxisContainerWidth)
            .padding(.top, 0)
            .padding(.bottom, 30)
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
