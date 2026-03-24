//
//  ThreeReadingAverageCard.swift
//  meApp
//
//  Full-width card showing the average of the last 3 BP readings.
//

import SwiftUI

struct ThreeReadingAverageCard: View {
    let average: ThreeReadingAverage
    @Environment(\.appTheme) private var theme
    @State private var showDetail = false

    var body: some View {
        Button {
            showDetail = true
        } label: {
            VStack(alignment: .leading, spacing: .spacingXS) {
                Text(average.label)
                    .fontOpenSans(.body3)
                    .foregroundColor(theme.textSubheading)
                    .textCase(.uppercase)

                HStack(alignment: .firstTextBaseline, spacing: .spacingSM) {
                    HStack(alignment: .firstTextBaseline, spacing: 2) {
                        Text("\(average.systolic)/\(average.diastolic)")
                            .fontOpenSans(.heading3)
                            .foregroundColor(average.classification.color(theme: theme))
                    }

                    HStack(spacing: 2) {
                        Image(systemName: "heart.fill")
                            .font(.caption)
                            .foregroundColor(theme.textSubheading)
                        Text("\(average.pulse)")
                            .fontOpenSans(.heading4)
                            .foregroundColor(theme.textSubheading)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.spacingSM)
            .background(theme.backgroundPrimaryDisabled)
            .cornerRadius(12)
        }
        .buttonStyle(.plain)
        .sheet(isPresented: $showDetail) {
            ThreeReadingAverageSheet(average: average)
        }
        .accessibilityLabel("\(average.label). Systolic \(average.systolic), Diastolic \(average.diastolic), Pulse \(average.pulse)")
    }
}
