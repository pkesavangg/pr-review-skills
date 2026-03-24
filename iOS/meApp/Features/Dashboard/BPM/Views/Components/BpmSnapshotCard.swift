//
//  BpmSnapshotCard.swift
//  meApp
//
//  Mini three-line BP graph card for the multi-device snapshot dashboard.
//

import Charts
import SwiftUI

struct BpmSnapshotCard: View {
    let summaries: [BathScaleWeightSummary]
    let onTap: () -> Void
    @Environment(\.appTheme) private var theme

    private var lastWeekSummaries: [BathScaleWeightSummary] {
        let sevenDaysAgo = Calendar.current.date(byAdding: .day, value: -7, to: Date()) ?? Date()
        return summaries.filter { $0.date >= sevenDaysAgo }
    }

    private var latestReading: (sys: Int, dia: Int, pulse: Int)? {
        guard let latest = lastWeekSummaries.last,
              let sys = latest.systolic,
              let dia = latest.diastolic else { return nil }
        return (Int(sys), Int(dia), Int(latest.pulse ?? 0))
    }

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: .spacingXS) {
                Text(ProductTypeStrings.myBloodPressure)
                    .fontOpenSans(.heading5)
                    .foregroundColor(theme.textHeading)

                if let reading = latestReading {
                    let classification = AhaPressureClass.classify(systolic: reading.sys, diastolic: reading.dia)
                    HStack(spacing: .spacingXS) {
                        Text("\(reading.sys)/\(reading.dia)")
                            .fontOpenSans(.body2)
                            .foregroundColor(classification.color(theme: theme))
                        Image(systemName: "heart.fill")
                            .font(.caption2)
                            .foregroundColor(theme.textSubheading)
                        Text("\(reading.pulse)")
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textSubheading)
                    }

                    miniChart()
                        .frame(height: 60)
                } else {
                    Text(BpmDashboardStrings.noReadingsYet)
                        .fontOpenSans(.body3)
                        .foregroundColor(theme.textSubheading)
                }
            }
            .padding(.spacingSM)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(theme.backgroundPrimaryDisabled)
            .cornerRadius(12)
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func miniChart() -> some View {
        let series = lastWeekSummaries.flatMap { op -> [(Date, Double, String)] in
            var pts: [(Date, Double, String)] = []
            if let sys = op.systolic { pts.append((op.date, sys, "systolic")) }
            if let dia = op.diastolic { pts.append((op.date, dia, "diastolic")) }
            if let pulse = op.pulse { pts.append((op.date, pulse, "pulse")) }
            return pts
        }
        Chart(series, id: \.2) { date, value, name in
            LineMark(x: .value("Date", date), y: .value("Value", value))
                .foregroundStyle(name == "pulse" ? theme.textSubheading : theme.statusSuccess)
        }
        .chartXAxis(.hidden)
        .chartYAxis(.hidden)
        .chartLegend(.hidden)
    }
}
