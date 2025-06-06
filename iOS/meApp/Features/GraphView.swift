//
//  GraphView.swift
//  meApp
//
//  Created by Lakshmi Priya on 06/06/25.
//

import SwiftUI
import Charts

struct WeightEntry: Identifiable {
    let id = UUID()
    let date: Date
    let weight: Double
}

// Sample data (only 5 readings)
let sampleWeights: [WeightEntry] = {
    let calendar = Calendar.current
    let now = Date()
    return (0..<5).map { offset in
        let baseWeight = 190.0 - Double(offset * 2)
        let fluctuation = Double.random(in: -1.5...1.5)
        let weight = round((baseWeight + fluctuation) * 10) / 10
        return WeightEntry(
            date: calendar.date(byAdding: .day, value: -offset * 7, to: now)!,
            weight: weight
        )
    }.reversed()
}()


struct WeightTrendChartView: View {
    @State private var selectedTab = 1 // 0: Week, 1: Month, 2: Year, 3: Label
    @Environment(\.appTheme) private var theme
    private let segmentTitles = ["WEEK", "MONTH", "YEAR", "LABEL"]

    var body: some View {
        VStack(spacing: 0) {
            // Top Section
            WeightHeaderView(weightText: "000.0", unitText: "lbs")


            // Chart Section
            GraphView(weights: sampleWeights)


            // Segmented Control
            SegmentedPicker(titles: segmentTitles, selectedIndex: $selectedTab)
            
        }
        .background(Color.white)
        .edgesIgnoringSafeArea(.all)
    }
}

#Preview {
    WeightTrendChartView()
}


import SwiftUI

struct SegmentedPicker: View {
    let titles: [String]
    @Binding var selectedIndex: Int

    var body: some View {
        HStack(spacing: 0) {
            ForEach(titles.indices, id: \.self) { idx in
                let title = titles[idx]
                Button(action: { selectedIndex = idx }) {
                    Text(title)
                        .fontWeight(.bold)
                        .foregroundColor(selectedIndex == idx ? .white : .black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .padding(.horizontal, 12)
                        .background(
                            selectedIndex == idx
                            ? Color(.black)
                            : Color.clear
                        )
                        .cornerRadius(12)
                }
            }
        }
        .padding(.vertical, 18)
        .padding(.horizontal, 15)
    }
}


import SwiftUI

struct WeightHeaderView: View {
    let weightText: String
    let unitText: String
    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(alignment: .bottom, spacing: 4) {
            Text(weightText)
                .fontOpenSans(.heading1)
                .foregroundColor(theme.textHeading)
            Text(unitText)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
                .offset(y: -16)
        }
        .padding(.leading, 14)
        .padding(.trailing, 195)
        .padding(.bottom, 16)
    }
}


import SwiftUI
import Charts

struct GraphView: View {
    let weights: [WeightEntry]
    @State private var selectedEntry: WeightEntry? = nil
    @Environment(\.appTheme) private var theme
    var yAxisTicks: [Double] {
        stride(from: 175, through: 190, by: 5).map { $0 }
    }

    var body: some View {
        ZStack(alignment: .trailing) {
            Chart {
                ForEach(weights) { entry in
                    LineMark(
                        x: .value("Date", entry.date),
                        y: .value("Weight", entry.weight)
                    )
                    .interpolationMethod(.catmullRom)
                    .foregroundStyle(theme.actionPrimary)
                    .lineStyle(StrokeStyle(lineWidth: 4))
                }

                ForEach(weights) { entry in
                    PointMark(
                        x: .value("Date", entry.date),
                        y: .value("Weight", entry.weight)
                    )
                    .symbolSize(selectedEntry?.id == entry.id ? 120 : 40)
                    .foregroundStyle(theme.actionPrimary)
                }

                // Stroked Y-axis lines
                ForEach(yAxisTicks, id: \.self) { tick in
                    RuleMark(y: .value("Y Grid", tick))
                        .lineStyle(StrokeStyle(lineWidth: 1, dash: [5, 5]))
                        .foregroundStyle(theme.textSubheading)
                }
            }
            .chartYScale(domain: 175...190)
            .chartYAxis {
                AxisMarks(values: .automatic) { value in
                    AxisTick()
                    AxisValueLabel() {
                        if let doubleValue = value.as(Double.self) {
                            Text("\(Int(doubleValue))")
                                .fontOpenSans(.subHeading2)
                        }
                    }
                }
            }
            .chartXAxis {
                AxisMarks(values: .automatic) { _ in
                    AxisTick()
                }
            }
            .frame(height: 300)
            .padding(.top, 16)
            .chartOverlay { proxy in
                GeometryReader { geometry in
                    Rectangle().fill(Color.clear).contentShape(Rectangle())
                        .gesture(
                            DragGesture(minimumDistance: 0)
                                .onEnded { value in
                                    let location = value.location
                                    if let date: Date = proxy.value(atX: location.x),
                                       let nearest = weights.min(by: { abs($0.date.timeIntervalSince(date)) < abs($1.date.timeIntervalSince(date)) }) {
                                        selectedEntry = nearest
                                    }
                                }
                        )
                }
            }
        }
    }
}
