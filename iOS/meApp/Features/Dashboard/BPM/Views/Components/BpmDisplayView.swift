//
//  BpmDisplayView.swift
//  meApp
//
//  Shows the systolic/diastolic headline and pulse, colored by AHA classification.
//  Reads from the store's display manager — selected point or visible window average.
//

import SwiftUI

struct BpmDisplayView: View {
    @ObservedObject var dashboardStore: DashboardStore
    @Environment(\.appTheme) private var theme

    private var displayValues: BpmDisplayData? {
        dashboardStore.displayManager?.getBpmDisplayValues()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: .zero) {
            if let values = displayValues {
                HStack(alignment: .top, spacing: .spacingXL) {
                    // Blood Pressure Section
                    VStack(alignment: .leading, spacing: 2) {
                        Text(BpmDashboardStrings.mmhg)
                            .fontOpenSans(.body3)
                            .foregroundColor(theme.textSubheading)

                        HStack(alignment: .firstTextBaseline, spacing: 2) {
                            Text("\(values.systolic)/\(values.diastolic)")
                                .fontOpenSans(.heading1)
                                .foregroundColor(values.classification.color(theme: theme))
                                .lineLimit(1)
                                .minimumScaleFactor(0.6)
                        }
                    }

                    // Pulse Section
                    VStack(alignment: .leading, spacing: 2) {
                        Text(BpmDashboardStrings.pulse)
                            .fontOpenSans(.body3)
                            .foregroundColor(theme.textSubheading)

                        Text("\(values.pulse)")
                            .fontOpenSans(.heading1)
                            .foregroundColor(theme.textSubheading)
                            .lineLimit(1)
                    }

                    Spacer()
                }
                .padding(.horizontal, .spacingSM)
                .padding(.vertical, .spacingXS)

                // Date range label — single source of truth
                if !values.label.isEmpty {
                    Text(values.label)
                        .fontOpenSans(.body3)
                        .foregroundColor(theme.textSubheading)
                        .padding(.leading, .spacingSM)
                }
            } else {
                Text("--/--")
                    .fontOpenSans(.heading1)
                    .foregroundColor(theme.textSubheading)
                    .padding(.horizontal, .spacingSM)
                    .padding(.vertical, .spacingXS)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel({
            guard let data = displayValues else { return "No blood pressure data" }
            return "Blood pressure \(data.systolic) over \(data.diastolic), pulse \(data.pulse), AHA classification: \(data.classification.label)"
        }())
    }
}
