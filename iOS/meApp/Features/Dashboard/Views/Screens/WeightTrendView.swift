//
//  GraphView.swift
//  meApp
//
//  Created by Lakshmi Priya on 06/06/25.
//

import SwiftUI

struct WeightTrendView: View {
    @StateObject private var graphStore = GraphStore()
    @Environment(\.appTheme) private var theme

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                HStack{
                    VStack(alignment: .leading, spacing: .zero ) {
                        Text("\(graphStore.selectedPeriod.rawValue) average")
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                            .padding(.leading, .spacingSM)

                        WeightDisplayView(
                            weightText: String(format: "%05.1f", graphStore.selectedEntry != nil
                                ? (graphStore.selectedEntry?.weight ?? 0)
                                : (graphStore.displayWeight ?? 0)),
                            unitText: "lbs"
                        )

                        if let label = graphStore.weightLabel {
                            Text(label)
                                .fontOpenSans(.subHeading2)
                                .foregroundColor(theme.textSubheading)
                                .padding(.leading, .spacingSM)
                                .padding(.top, .spacingXS)
                        }
                    }

                    Spacer()
                }
                .padding(.bottom, 8)

                GraphView(graphStore: graphStore)
                    .padding(.trailing, 10)

                SegmentedButtonView(
                    segments: TimePeriod.allCases,
                    selectedSegment: Binding(
                        get: { graphStore.selectedPeriod },
                        set: { graphStore.updateSelectedPeriod($0) }
                    )
                )
                .padding(.bottom, .spacingSM)
                .padding(.horizontal, 15)
            }
            .padding(.top, .spacingLG)
            .background(theme.textInverse)
            .edgesIgnoringSafeArea(.all)
            .zIndex(1)
        }
        .onAppear {
            let sampleData = sampleOperations(for: graphStore.selectedPeriod.rawValue)
            graphStore.updateOperations(sampleData)
        }
        .onChange(of: graphStore.selectedPeriod) { _, newValue in
            let newOperations = sampleOperations(for: newValue.rawValue)
            graphStore.updateOperations(newOperations)
        }
    }
}

#Preview {
    WeightTrendView()
}

// Temporary mock data for BathScaleOperationDTO used for preview/testing purposes.
func sampleOperations(for segment: String) -> [BathScaleOperationDTO] {
    let calendar = Calendar.current
    let today = calendar.startOfDay(for: Date())
    var operations: [BathScaleOperationDTO] = []

    func createOperation(date: Date, weight: Double) -> BathScaleOperationDTO {
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
    }

    switch segment.uppercased() {
    case "WEEK":
        // Generate data for the past 60 days (much more than visible domain of 7 days)
        operations = (0..<60).map { offset in
            let date = calendar.date(byAdding: .day, value: -offset, to: today)!
            let baseWeight = 180.0
            let variation = sin(Double(offset) / 7.0) * 2.0 + Double.random(in: -1.0...1.0)
            return createOperation(date: date, weight: baseWeight + variation)
        }.sorted { ($0.date ?? Date()) < ($1.date ?? Date()) }

    case "MONTH":
        // Generate data for the past 120 days (much more than visible domain of 30 days)
        operations = (0..<120).map { offset in
            let date = calendar.date(byAdding: .day, value: -offset, to: today)!
            let baseWeight = 180.0
            let trend = -Double(offset) * 0.02 // Gradual weight loss trend
            let variation = sin(Double(offset) / 10.0) * 3.0 + Double.random(in: -1.5...1.5)
            return createOperation(date: date, weight: baseWeight + trend + variation)
        }.sorted { ($0.date ?? Date()) < ($1.date ?? Date()) }

    case "YEAR":
        // Generate data for the past 2 years (730 days, much more than visible domain of 365 days)
        operations = (0..<730).map { offset in
            let date = calendar.date(byAdding: .day, value: -offset, to: today)!
            let baseWeight = 185.0
            let yearlyTrend = -Double(offset) * 0.01 // Very gradual weight loss over time
            let seasonalVariation = sin(Double(offset) / 365.0 * 2 * Double.pi) * 5.0 // Seasonal variation
            let randomVariation = Double.random(in: -2.0...2.0)
            return createOperation(date: date, weight: baseWeight + yearlyTrend + seasonalVariation + randomVariation)
        }.sorted { ($0.date ?? Date()) < ($1.date ?? Date()) }

    case "TOTAL":
        // Generate data for the past 3 years (1095 days)
        operations = (0..<1095).map { offset in
            let date = calendar.date(byAdding: .day, value: -offset, to: today)!
            let baseWeight = 190.0
            let longTermTrend = -Double(offset) * 0.008 // Very gradual long-term weight loss
            let yearlyVariation = sin(Double(offset) / 365.0 * 2 * Double.pi) * 4.0
            let monthlyVariation = sin(Double(offset) / 30.0 * 2 * Double.pi) * 2.0
            let randomVariation = Double.random(in: -2.5...2.5)
            return createOperation(date: date, weight: baseWeight + longTermTrend + yearlyVariation + monthlyVariation + randomVariation)
        }.sorted { ($0.date ?? Date()) < ($1.date ?? Date()) }

    default:
        // Default case - generate 30 days of data
        operations = (0..<30).map { offset in
            let date = calendar.date(byAdding: .day, value: -offset, to: today)!
            let baseWeight = 178.0
            let variation = Double.random(in: -1.0...1.0)
            return createOperation(date: date, weight: baseWeight + variation)
        }.sorted { ($0.date ?? Date()) < ($1.date ?? Date()) }
    }

    return operations
}
