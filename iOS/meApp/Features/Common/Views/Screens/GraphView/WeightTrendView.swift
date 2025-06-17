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
// Remove this block when integrating with live data from the Bluetooth scale or backend.
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
    return Array((0..<count).map { offset in
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
    }.reversed())
}
