//
//  BpmDisplayView.swift
//  meApp
//
//  Shows the systolic/diastolic headline and pulse, colored by AHA classification.
//  Layout mirrors weightInfoSection + WeightDisplayView for consistent spacing.
//

import SwiftUI

struct BpmDisplayView: View {
    private enum Layout {
        static let helpButtonTopPadding: CGFloat = 6
        static let helpButtonHorizontalPadding: CGFloat = 4
        static let headlineLeadingPadding: CGFloat = 14
        static let rowHeight: CGFloat = 55
        static let valueSpacing: CGFloat = 6
        static let unitSpacing: CGFloat = 4
    }

    @ObservedObject var dashboardStore: DashboardStore
    @Environment(\.appTheme) private var theme
    @State private var showAhaRatingSheet = false

    private var displayValues: BpmDisplayData? {
        dashboardStore.displayManager?.getBpmDisplayValues()
    }

    /// Reuses the same label logic as weight: "no entries", "week average", "day average", etc.
    private var displayLabel: String {
        dashboardStore.displayManager?.weightDisplayLabel ?? BpmDashboardStrings.noEntries
    }

    var body: some View {
        VStack(alignment: .leading, spacing: .zero) {
            Text(displayLabel)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
                .padding(.leading, .spacingSM)

            if let values = displayValues {
                HStack(alignment: .lastTextBaseline, spacing: 0) {
                    HStack(alignment: .top, spacing: Layout.valueSpacing) {
                        HStack(alignment: .lastTextBaseline, spacing: Layout.unitSpacing) {
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
                                .padding(.top, Layout.helpButtonTopPadding)
                                .padding(.horizontal, Layout.helpButtonHorizontalPadding)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(BpmDashboardStrings.openAhaRatings)
                    }

                    Spacer()

                    HStack(alignment: .lastTextBaseline, spacing: Layout.unitSpacing) {
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
                .padding(.leading, Layout.headlineLeadingPadding)
                .padding(.trailing, .spacingSM)
                .frame(height: Layout.rowHeight)
            } else {
                HStack(alignment: .lastTextBaseline, spacing: Layout.unitSpacing) {
                    Text(BpmDashboardStrings.bpPlaceholder)
                        .fontWeight(.heavy)
                        .fontOpenSans(.heading1)
                        .foregroundColor(theme.textSubheading)
                }
                .padding(.leading, Layout.headlineLeadingPadding)
                .frame(height: Layout.rowHeight)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel({
            guard let data = displayValues else { return BpmDashboardStrings.noBloodPressureData }
            return BpmDashboardStrings.bpReadingAccessibility(systolic: data.systolic, diastolic: data.diastolic, pulse: data.pulse, label: data.classification.label)
        }())
        .sheet(isPresented: $showAhaRatingSheet) {
            AhaRatingSheet()
        }
    }
}
