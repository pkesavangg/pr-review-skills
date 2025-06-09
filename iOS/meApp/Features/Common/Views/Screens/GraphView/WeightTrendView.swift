//
//  GraphView.swift
//  meApp
//
//  Created by Lakshmi Priya on 06/06/25.
//

import SwiftUI
import Charts

struct WeightTrendView: View {
    @State private var selectedSegment: TimePeriod = .month
    @Environment(\.appTheme) private var theme
    
    var body: some View {
        ZStack {
            // Background and main content
            VStack(spacing: 0) {
                // Top Section
                WeightDisplayView(weightText: "000.0", unitText: "lbs")
                
                GraphView(operations: sampleOperations(for: selectedSegment.rawValue), selectedSegmentTitle: selectedSegment.displayName)
                
                // Segmented Control
                SegmentedPickerView(
                    segments: TimePeriod.allCases,
                    selectedSegment: $selectedSegment
                )
            }
            .background(theme.textInverse)
            .edgesIgnoringSafeArea(.all)
            .zIndex(1) // Lower z-index for main content
        }
    }
}

#Preview {
    WeightTrendView()
}



// PreferenceKey to pass annotation height up the view tree
private struct AnnotationHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

// Sample data (only 5 readings) for BathScaleOperationDTO
func sampleOperations(for segment: String) -> [BathScaleOperationDTO] {
    let calendar = Calendar.current
    let now = Date()
    let count: Int
    switch segment.uppercased() {
    case "WEEK":
        count = Int.random(in: 5...7)
    case "MONTH":
        count = Int.random(in: 7...10)
    case "YEAR":
        count = 12
    case "LABEL":
        count = 15
    default:
        count = 7
    }
    return (0..<count).map { offset in
        // Pick a weight in the range 176...189 (inclusive), randomly, with 1 decimal
        let weight = Double(Int.random(in: 1760...1890)) / 10.0
        let date: Date
        switch segment.uppercased() {
        case "WEEK":
            date = calendar.date(byAdding: .day, value: -offset, to: now)!
        case "MONTH":
            date = calendar.date(byAdding: .day, value: -offset, to: now)!
        case "YEAR":
            date = calendar.date(byAdding: .month, value: -offset, to: now)!
        case "LABEL":
            date = calendar.date(byAdding: .day, value: -offset*2, to: now)!
        default:
            date = calendar.date(byAdding: .day, value: -offset, to: now)!
        }
        let isoString = ISO8601DateFormatter().string(from: date)
        return BathScaleOperationDTO(
            accountId: nil,
            bmr: nil,
            bmi: nil,
            bodyFat: nil,
            boneMass: nil,
            entryTimestamp: isoString,
            impedance: nil,
            metabolicAge: nil,
            muscleMass: nil,
            operationType: nil,
            proteinPercent: nil,
            pulse: nil,
            serverTimestamp: nil,
            skeletalMusclePercent: nil,
            source: nil,
            subcutaneousFatPercent: nil,
            unit: nil,
            visceralFatLevel: nil,
            water: nil,
            weight: weight
        )
    }.reversed()
}


// Extend BathScaleOperationDTO to conform to Identifiable and provide a computed date property
extension BathScaleOperationDTO: Identifiable {
    var id: String { entryTimestamp ?? UUID().uuidString }
    var date: Date? {
        guard let entryTimestamp = entryTimestamp else { return nil }
        let formatter = ISO8601DateFormatter()
        return formatter.date(from: entryTimestamp)
    }
}

// MARK: - GraphView

struct GraphView: View {
    let operations: [BathScaleOperationDTO]
    let selectedSegmentTitle: String
    @State private var selectedEntry: BathScaleOperationDTO? = nil
    @State private var annotationHeight: CGFloat = 0
    @State private var selectedPointY: CGFloat = 0
    @State private var chartHeight: CGFloat = 0
    @Environment(\.appTheme) private var theme

    var yAxisTicks: [Double] {
        stride(from: 175, through: 190, by: 5).map { $0 }
    }

    var body: some View {
        ZStack(alignment: .trailing) {
            // Chart content
            Chart {
                ForEach(yAxisTicks, id: \.self) { tick in
                    chartGridLine(tick: tick)
                }
                if let selected = selectedEntry, let date = selected.date, let weight = selected.weight {
                    chartRuleMark(date: date, weight: weight, selected: selected)
                }
                chartLineMarks()
                chartPointMarks()
            }
            .chartYScale(domain: 175...190)
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
                    Color.clear
                        .onAppear { chartHeight = geo.size.height }
                        .onChange(of: geo.size.height) { chartHeight = $0 }
                }
            )
            .padding(.top, 16)
            .chartOverlay { proxy in
                GeometryReader { _ in
                    Color.clear
                        .contentShape(Rectangle())
                        .gesture(dragGesture(proxy: proxy))
                }
            }
            .onPreferenceChange(AnnotationHeightKey.self) { annotationHeight = $0 }
            .zIndex(1) // Lower z-index for chart content
        }
    }

    // MARK: - Modularized subviews

    @ChartContentBuilder
    private func chartGridLine(tick: Double) -> some ChartContent {
        RuleMark(y: .value("Y Grid", tick))
            .lineStyle(StrokeStyle(lineWidth: 1, dash: [5, 5]))
            .foregroundStyle(theme.iconUtility)
            .zIndex(-1)
        
    }

    @ChartContentBuilder
    private func chartRuleMark(date: Date, weight: Double, selected: BathScaleOperationDTO) -> some ChartContent {
        RuleMark(x: .value("Selected Date", date))
            .lineStyle(StrokeStyle(lineWidth: 1))
            .foregroundStyle(theme.iconUtility)
            .annotation(position: .top, alignment: .center) {
                ruleMarkDateLabel(date: date)
            }
            .annotation(position: .bottom, alignment: .center) {
                let pointRadius: CGFloat = 60 // 120 symbolSize => 60 radius
                let bubbleHeight = annotationHeight
                let offsetY = selectedPointY - (chartHeight / 2) + pointRadius + 5 - (bubbleHeight / 2) - 200
                Text("\(Int(weight))")
                    .fontWeight(.bold)
                    .fontOpenSans(.body3)
                    .foregroundColor(theme.textInverse)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 1)
                    .background(Capsule().fill(theme.iconGoal))
                    .background(
                        GeometryReader { bubbleGeo in
                            Color.clear
                                .preference(key: AnnotationHeightKey.self, value: bubbleGeo.size.height)
                        }
                    )
                    .offset(y: offsetY)
                    .zIndex(100) // Highest z-index for annotation
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
                .symbolSize(selectedEntry?.id == entry.id ? 120 : 40)
                .foregroundStyle(theme.actionPrimary)
            }
        }
    }

    // MARK: - Helpers

    @ViewBuilder
    private func ruleMarkDateLabel(date: Date) -> some View {
        let dateText = selectedSegmentTitle == TimePeriod.week.rawValue
            ? date.formatted(.dateTime.day(.twoDigits).month(.abbreviated).year(.defaultDigits))
            : date.formatted(.dateTime.month(.abbreviated).year(.defaultDigits))

        Text(dateText.lowercased())
            .fontOpenSans(.subHeading2)
            .fontWeight(.semibold)
            .foregroundColor(theme.textSubheading)
            .padding(.vertical, 1.5)
    }

    private func dragGesture(proxy: ChartProxy) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                updateSelectedEntry(at: value.location, using: proxy)
            }
            .onEnded { value in
                updateSelectedEntry(at: value.location, using: proxy)
            }
    }

    private func updateSelectedEntry(at location: CGPoint, using proxy: ChartProxy) {
        if let date: Date = proxy.value(atX: location.x) {
            if let nearest = operations
                .compactMap({ op -> (BathScaleOperationDTO, Date)? in
                    guard let d = op.date else { return nil }
                    return (op, d)
                })
                .min(by: { abs($0.1.timeIntervalSince(date)) < abs($1.1.timeIntervalSince(date)) })?.0 {
                selectedEntry = nearest
                if let yVal = nearest.weight {
                    if let yInView = proxy.position(forY: yVal) {
                        selectedPointY = yInView
                    }
                }
            }
        }
    }
}
