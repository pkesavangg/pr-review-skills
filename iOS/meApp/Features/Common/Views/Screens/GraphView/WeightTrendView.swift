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
    @State private var selectedWeight: Double? = nil
    @State private var operations: [BathScaleOperationDTO] = sampleOperations(for: TimePeriod.month.rawValue)
    @Environment(\.appTheme) private var theme
    
    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                
                VStack(alignment: .leading){
                    WeightDisplayView(
                        weightText: String(format: "%05.1f", selectedWeight ?? 0),
                        unitText: "lbs"
                    )
                    
                    Text("jun 7 - 13, 2024")
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.textSubheading)
                        .padding(.leading, 16)
                        .padding(.top, 10)
                }
                .padding(.bottom, 8)

                GraphView(
                    operations: operations,
                    selectedSegmentTitle: selectedSegment.displayName,
                    selectedWeight: $selectedWeight
                )

                SegmentedButtonView(
                    segments: TimePeriod.allCases,
                    selectedSegment: $selectedSegment
                )
                .padding(.top, 18)
            }
            .background(theme.textInverse)
            .edgesIgnoringSafeArea(.all)
            .zIndex(1)
        }
        .onChange(of: selectedSegment) { oldValue, newValue in
            self.operations = sampleOperations(for: newValue.rawValue)
            self.selectedWeight = operations.last?.weight
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
    
    switch segment.uppercased() {
    case "WEEK":
        // Generate data for the last 2 weeks
        for dayOffset in (-13...0) {
            let date = calendar.date(byAdding: .day, value: dayOffset, to: now)!
            // Generate 1-2 entries per day
            let entriesPerDay = Int.random(in: 1...2)
            for _ in 0..<entriesPerDay {
                let weight = Double(Int.random(in: 1760...1890)) / 10.0
                let hourOffset = Int.random(in: 0...23)
                let minuteOffset = Int.random(in: 0...59)
                let entryDate = calendar.date(bySettingHour: hourOffset, minute: minuteOffset, second: 0, of: date)!
                operations.append(createOperation(date: entryDate, weight: weight))
            }
        }
        
    case "MONTH":
        // Generate data for the last 2 months
        for dayOffset in (-60...0) {
            let date = calendar.date(byAdding: .day, value: dayOffset, to: now)!
            // 70% chance of having an entry each day
            if Double.random(in: 0...1) < 0.7 {
                let weight = Double(Int.random(in: 1760...1890)) / 10.0
                let hourOffset = Int.random(in: 0...23)
                let minuteOffset = Int.random(in: 0...59)
                let entryDate = calendar.date(bySettingHour: hourOffset, minute: minuteOffset, second: 0, of: date)!
                operations.append(createOperation(date: entryDate, weight: weight))
            }
        }
        
    case "YEAR":
        // Generate data for the last 2 years
        for monthOffset in (-24...0) {
            let date = calendar.date(byAdding: .month, value: monthOffset, to: now)!
            // Generate 2-4 entries per month
            let entriesPerMonth = Int.random(in: 2...4)
            for _ in 0..<entriesPerMonth {
                let weight = Double(Int.random(in: 1760...1890)) / 10.0
                let dayOffset = Int.random(in: 1...28)
                let hourOffset = Int.random(in: 0...23)
                let minuteOffset = Int.random(in: 0...59)
                if let entryDate = calendar.date(bySetting: .day, value: dayOffset, of: date)?
                    .addingTimeInterval(Double(hourOffset * 3600 + minuteOffset * 60)) {
                    operations.append(createOperation(date: entryDate, weight: weight))
                }
            }
        }
        
    case "TOTAL":
        // Generate data for the last 3 years
        for monthOffset in (-36...0) {
            let date = calendar.date(byAdding: .month, value: monthOffset, to: now)!
            // Generate 1-3 entries per month
            let entriesPerMonth = Int.random(in: 1...3)
            for _ in 0..<entriesPerMonth {
                let weight = Double(Int.random(in: 1760...1890)) / 10.0
                let dayOffset = Int.random(in: 1...28)
                let hourOffset = Int.random(in: 0...23)
                let minuteOffset = Int.random(in: 0...59)
                if let entryDate = calendar.date(bySetting: .day, value: dayOffset, of: date)?
                    .addingTimeInterval(Double(hourOffset * 3600 + minuteOffset * 60)) {
                    operations.append(createOperation(date: entryDate, weight: weight))
                }
            }
        }
        
    default:
        // Default to week view
        for dayOffset in (-6...0) {
            let date = calendar.date(byAdding: .day, value: dayOffset, to: now)!
            let weight = Double(Int.random(in: 1760...1890)) / 10.0
            let hourOffset = Int.random(in: 0...23)
            let minuteOffset = Int.random(in: 0...59)
            let entryDate = calendar.date(bySettingHour: hourOffset, minute: minuteOffset, second: 0, of: date)!
            operations.append(createOperation(date: entryDate, weight: weight))
        }
    }
    
    return operations.sorted { $0.date ?? Date() < $1.date ?? Date() }
}

private func createOperation(date: Date, weight: Double) -> BathScaleOperationDTO {
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
