//
//  ThreeReadingAverageCard.swift
//  meApp
//
//  Full-width card showing the average of the last 3 BP readings.
//

import SwiftUI

struct ThreeReadingAverageCard: View {
    let average: ThreeReadingAverage
    let recentReadings: [BpmReadingDisplayData]
    @State private var showDetail = false

    var body: some View {
        Button {
            showDetail = true
        } label: {
            BpmSummaryCardView(
                systolic: average.systolic,
                diastolic: average.diastolic,
                pulse: average.pulse,
                classification: average.classification,
                footer: .centered(average.label)
            )
        }
        .buttonStyle(.plain)
        .sheet(isPresented: $showDetail) {
            ThreeReadingAverageSheet(average: average, readings: recentReadings)
        }
        .accessibilityLabel(BpmDashboardStrings.bpAverageCardAccessibility(
            label: average.label, systolic: average.systolic,
            diastolic: average.diastolic, pulse: average.pulse
        ))
    }
}
