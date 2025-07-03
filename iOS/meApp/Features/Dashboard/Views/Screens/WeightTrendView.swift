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
                            weightText: String(format: "%05.1f", graphStore.selectedEntry?.weight ?? graphStore.displayWeight ?? 0),
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
                    .frame(height: 400)
                    .padding(.trailing, 10)
                
                SegmentedButtonView(
                    segments: TimePeriod.allCases,
                    selectedSegment: Binding(
                        get: { graphStore.selectedPeriod },
                        set: { graphStore.updateSelectedPeriod($0) }
                    )
                )
                .padding(.bottom, .spacingSM)
                .padding(.top, 18)
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
    let now = Date()
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

    // Helper to get date for a given day offset from now, at a specific time
    func dateByAdding(days: Int, hour: Int = 8, minute: Int = 0) -> Date {
        let base = calendar.date(byAdding: .day, value: days, to: now)!
        return calendar.date(bySettingHour: hour, minute: minute, second: 0, of: base)!
    }
    func dateByAdding(months: Int, day: Int = 1, hour: Int = 8, minute: Int = 0) -> Date {
        let base = calendar.date(byAdding: .month, value: months, to: now)!
        let withDay = calendar.date(bySetting: .day, value: day, of: base)!
        return calendar.date(bySettingHour: hour, minute: minute, second: 0, of: withDay)!
    }

    switch segment.uppercased() {
    case "WEEK":
        // Generate data for the current week (7 days)
        operations = [
                   // Page 1
                   createOperation(date: dateByAdding(days: -16), weight: 180.1),
                   createOperation(date: dateByAdding(days: -15), weight: 179.8),
                   createOperation(date: dateByAdding(days: -14), weight: 180.1),
                   createOperation(date: dateByAdding(days: -13), weight: 179.8),
                   createOperation(date: dateByAdding(days: -12), weight: 180.2),
                   // Page 2
                   createOperation(date: dateByAdding(days: -7), weight: 179.0),
                   createOperation(date: dateByAdding(days: -6), weight: 178.7),
                   createOperation(date: dateByAdding(days: -5), weight: 179.3),
                   // Page 3
                   createOperation(date: dateByAdding(days: -2), weight: 178.1),
                   createOperation(date: dateByAdding(days: -1), weight: 177.9),
                   createOperation(date: dateByAdding(days: 0), weight: 178.0)
               ]

    case "MONTH":
        // 3 months, 3 pages, 4 entries each (spaced about 1 week apart)
        // Page 1: 2 months ago
        operations = [
            createOperation(date: dateByAdding(months: -2, day: 2), weight: 185.1),
            createOperation(date: dateByAdding(months: -2, day: 8), weight: 184.5),
            createOperation(date: dateByAdding(months: -2, day: 15), weight: 183.8),
            createOperation(date: dateByAdding(months: -2, day: 22), weight: 183.2),
            // Page 2: 1 month ago
            createOperation(date: dateByAdding(months: -1, day: 2), weight: 182.7),
            createOperation(date: dateByAdding(months: -1, day: 9), weight: 182.1),
            createOperation(date: dateByAdding(months: -1, day: 16), weight: 181.5),
            createOperation(date: dateByAdding(months: -1, day: 23), weight: 181.0),
            // Page 3: current month
            createOperation(date: dateByAdding(months: 0, day: 2), weight: 180.7),
            createOperation(date: dateByAdding(months: 0, day: 9), weight: 180.2),
            createOperation(date: dateByAdding(months: 0, day: 16), weight: 179.8),
            createOperation(date: dateByAdding(months: 0, day: 23), weight: 179.3)
        ]

    case "YEAR":
        // Two years: one entry every 2 months (12 entries)
        let monthsAgo = stride(from: -22, through: 0, by: 2).map { $0 }
        let weights: [Double] = [188.0, 183.4, 185.1, 181.9, 184.2, 180.7, 182.5, 179.8, 181.2, 178.6, 180.4, 177.9]
        operations = zip(monthsAgo, weights).map { (monthOffset, weight) in
            createOperation(date: dateByAdding(months: monthOffset, day: 8), weight: weight)
        }

    case "TOTAL":
        // 13 entries over 3 years, every 3 months
        let monthsAgo = stride(from: -36, through: 0, by: 3).map { $0 }
        let weights: [Double] = [182.8, 185.0, 179.4, 183.3, 180.1, 178.9, 181.7, 179.2, 180.5, 177.4, 179.6, 178.2, 177.3]
        operations = zip(monthsAgo, weights).map { (monthOffset, weight) in
            createOperation(date: dateByAdding(months: monthOffset, day: 10), weight: weight)
        }


    default:
        // Default to recent week, 3 entries
        operations = [
            createOperation(date: dateByAdding(days: -2), weight: 178.1),
            createOperation(date: dateByAdding(days: -1), weight: 177.9),
            createOperation(date: dateByAdding(days: 0), weight: 178.0)
        ]
    }

    return operations.sorted { $0.date ?? Date() < $1.date ?? Date() }
}
