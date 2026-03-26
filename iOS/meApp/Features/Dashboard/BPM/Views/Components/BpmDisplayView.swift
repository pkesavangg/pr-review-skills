//
//  BpmDisplayView.swift
//  meApp
//
//  Shows the systolic/diastolic headline and pulse, colored by AHA classification.
//  Layout mirrors weightInfoSection + WeightDisplayView for consistent spacing.
//

import SwiftUI

struct BpmDisplayView: View {
    @ObservedObject var dashboardStore: DashboardStore
    @Environment(\.appTheme) private var theme
    @State private var showAhaRatingSheet = false

    private var displayValues: BpmDisplayData? {
        dashboardStore.displayManager?.getBpmDisplayValues()
    }

    /// Reuses the same label logic as weight: "no entries", "week average", "day average", etc.
    private var displayLabel: String {
        dashboardStore.displayManager?.weightDisplayLabel ?? "no entries"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: .zero) {
            Text(displayLabel)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
                .padding(.leading, .spacingSM)

            if let values = displayValues {
                HStack(alignment: .lastTextBaseline, spacing: 0) {
                    HStack(alignment: .top, spacing: 6) {
                        HStack(alignment: .lastTextBaseline, spacing: 4) {
                            Text("\(values.systolic)/\(values.diastolic)")
                                .fontWeight(.heavy)
                                .fontOpenSans(.heading1)
                                .foregroundColor(values.classification.color(theme: theme))
                                .lineLimit(1)
                                .minimumScaleFactor(0.6)

                            Text(BpmDashboardStrings.mmhg)
                                .fontOpenSans(.subHeading2)
                                .foregroundColor(theme.textSubheading)
                        }

                        Button {
                            showAhaRatingSheet = true
                        } label: {
                            AppIconView(icon: AppAssets.helpCircle, size: IconSize(width: 16, height: 16))
                                .foregroundColor(theme.textSubheading)
                                .padding(.top, 6)
                                .padding(.horizontal, 4)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Open AHA ratings")
                    }

                    Spacer()

                    HStack(alignment: .lastTextBaseline, spacing: 4) {
                        Text("\(values.pulse)")
                            .fontWeight(.heavy)
                            .fontOpenSans(.heading1)
                            .foregroundColor(theme.textSubheading)
                            .lineLimit(1)
                            .minimumScaleFactor(0.6)

                        Text(BpmDashboardStrings.bpm)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                    }
                }
                .padding(.leading, 14)
                .padding(.trailing, .spacingSM)
                .frame(height: 55)
            } else {
                HStack(alignment: .lastTextBaseline, spacing: 4) {
                    Text("--/--")
                        .fontWeight(.heavy)
                        .fontOpenSans(.heading1)
                        .foregroundColor(theme.textSubheading)
                }
                .padding(.leading, 14)
                .frame(height: 55)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel({
            guard let data = displayValues else { return "No blood pressure data" }
            return "Blood pressure \(data.systolic) over \(data.diastolic), pulse \(data.pulse), \(data.classification.label)"
        }())
        .sheet(isPresented: $showAhaRatingSheet) {
            AhaRatingSheet()
        }
    }
}
