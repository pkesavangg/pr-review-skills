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

    var body: some View {
        ZStack(alignment: .trailing) {
            Chart {
                ForEach(viewModel.yAxisTicks, id: \.self) { tick in
                    chartGridLine(tick: tick)
                }
                if let selected = viewModel.selectedEntry, let date = selected.date, let weight = selected.weight {
                    chartRuleMark(date: date, weight: weight, selected: selected)
                }
                chartLineMarks()
                chartPointMarks()
            }
            .chartYScale(domain: 175...190)
            .chartXScale(domain: viewModel.xAxisDomain(for: operations))
            .chartYAxis {
                AxisMarks(values: .automatic) { value in
                    AxisTick()
                    AxisValueLabel {
                        if let doubleValue = value.as(Double.self) {
                            Text("\(Int(doubleValue))")
                                .fontOpenSans(.subHeading2)
                                .zIndex(1)
                        }
                    }
                }
            }
            .chartXAxis {
                AxisMarks(values: .automatic) { _ in AxisTick() }
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
                            viewModel.dragGesture(
                                proxy: proxy,
                                operations: operations,
                                selectedWeight: $selectedWeight
                            )
                        )
                }
            }
            .onPreferenceChange(AnnotationHeightKey.self) { viewModel.annotationHeight = $0 }
            .zIndex(1)
        }
    }

    // MARK: - ChartContentBuilders

    @ChartContentBuilder
    private func chartGridLine(tick: Double) -> some ChartContent {
        RuleMark(y: .value("Y Grid", tick))
            .lineStyle(StrokeStyle(lineWidth: 1, dash: [5, 5]))
            .foregroundStyle(theme.statusUtility)
            .zIndex(-1)
    }

    @ChartContentBuilder
    private func chartRuleMark(date: Date, weight: Double, selected: BathScaleOperationDTO) -> some ChartContent {
        RuleMark(x: .value("Selected Date", date))
            .lineStyle(StrokeStyle(lineWidth: 1))
            .foregroundStyle(theme.statusUtility)
            .annotation(position: .top, alignment: viewModel.ruleMarkAlignment(for: selected, in: operations)) {
                ruleMarkDateLabel(date: date)
            }
            .annotation(position: .bottom, alignment: viewModel.ruleMarkAlignment(for: selected, in: operations)) {
                let offsetY = viewModel.annotationBubbleOffset()
                Text("\(Int(weight))")
                    .fontWeight(.bold)
                    .fontOpenSans(.body3)
                    .foregroundColor(theme.textInverse)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 1)
                    .background(Capsule().fill(theme.statusSuccess))
                    .background(
                        GeometryReader { bubbleGeo in
                            theme.textInverse
                                .preference(key: AnnotationHeightKey.self, value: bubbleGeo.size.height)
                        }
                    )
                    .offset(y: offsetY)
                    .zIndex(100)
            }
    }

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
                .symbolSize(viewModel.selectedEntry?.id == entry.id ? 120 : 40)
                .foregroundStyle(theme.actionPrimary)
            }
        }
    }

    @ViewBuilder
    private func ruleMarkDateLabel(date: Date) -> some View {
        let dateText = selectedSegmentTitle == TimePeriod.week.displayName
            ? date.formatted(.dateTime.day(.twoDigits).month(.abbreviated).year(.defaultDigits))
            : date.formatted(.dateTime.month(.abbreviated).year(.defaultDigits))

        Text(dateText.lowercased())
            .fontOpenSans(.subHeading2)
            .fontWeight(.semibold)
            .foregroundColor(theme.textSubheading)
            .padding(.vertical, 1.5)
    }
}
