//
//  BabyEmptyGraphView.swift
//  meApp
//
//  Placeholder week grid shown on the baby dashboard when the baby has no
//  weight / height / history data yet. Draws a clean empty chart skeleton
//  (horizontal gridlines, dashed weekday gridlines, y-axis 10–25, weekday
//  labels) matching the design, with no data or percentile curves.
//

import SwiftUI

struct BabyEmptyGraphView: View {
    @Environment(\.appTheme) private var theme

    /// Height of the plot area; matches the proportions of the design mock.
    var plotHeight: CGFloat = 240

    private let weekdays = ["sun", "mon", "tue", "wed", "thu", "fri", "sat"]
    /// Y-axis ticks, top → bottom.
    private let yTicks = [25, 20, 15, 10]
    private let yAxisWidth: CGFloat = 28

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: .spacingXS) {
                plotGrid
                    .frame(maxWidth: .infinity)
                    .frame(height: plotHeight)

                yAxisLabels
                    .frame(width: yAxisWidth, height: plotHeight)
            }

            weekdayLabels
                .padding(.top, .spacingXS)
                .padding(.trailing, yAxisWidth + .spacingXS)
        }
        .padding(.leading, .spacingSM)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(DashboardStrings.noEntries)
    }

    // MARK: - Plot grid

    private var plotGrid: some View {
        ZStack {
            // Horizontal gridlines at each y-axis tick.
            VStack(spacing: 0) {
                ForEach(Array(yTicks.enumerated()), id: \.offset) { index, _ in
                    if index > 0 { Spacer(minLength: 0) }
                    Rectangle()
                        .fill(theme.statusIconSecondaryDisabled)
                        .frame(height: 1)
                }
            }

            // Dashed vertical gridlines, one centred per weekday column.
            HStack(spacing: 0) {
                ForEach(Array(weekdays.enumerated()), id: \.offset) { _, _ in
                    verticalDashedLine
                        .frame(maxWidth: .infinity)
                }
            }
        }
    }

    private var verticalDashedLine: some View {
        GeometryReader { geo in
            Path { path in
                let midX = geo.size.width / 2
                path.move(to: CGPoint(x: midX, y: 0))
                path.addLine(to: CGPoint(x: midX, y: geo.size.height))
            }
            .stroke(
                theme.statusIconSecondaryDisabled.opacity(0.6),
                style: StrokeStyle(lineWidth: 1, dash: [3, 3])
            )
        }
    }

    // MARK: - Axis labels

    private var yAxisLabels: some View {
        VStack(spacing: 0) {
            ForEach(Array(yTicks.enumerated()), id: \.offset) { index, tick in
                if index > 0 { Spacer(minLength: 0) }
                Text("\(tick)")
                    .fontOpenSans(.subHeading2)
                    .fontWeight(.regular)
                    .monospacedDigit()
                    .foregroundColor(theme.textSubheading)
            }
        }
    }

    private var weekdayLabels: some View {
        HStack(spacing: 0) {
            ForEach(Array(weekdays.enumerated()), id: \.offset) { _, day in
                Text(day)
                    .fontOpenSans(.subHeading2)
                    .fontWeight(.regular)
                    .foregroundColor(theme.textSubheading)
                    .frame(maxWidth: .infinity)
            }
        }
    }
}

#Preview {
    BabyEmptyGraphView()
        .padding()
}
