//
//  ThreeReadingAverageSheet.swift
//  meApp
//
//  Detail sheet: "Why We Take an Average" + last 3 reading rows.
//

import SwiftUI

struct ThreeReadingAverageSheet: View {
    let average: ThreeReadingAverage
    let readings: [BpmReadingDisplayData]
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView<AnyView, EmptyView>(
                title: BpmDashboardStrings.threeReadingAverageTitle,
                leadingContent: {
                    AnyView(
                        AppIconView(icon: AppAssets.close)
                            .foregroundColor(theme.actionSecondary)
                    )
                },
                onLeadingTap: { dismiss() },
                canShowBorder: true,
                canShowPresentationIndicator: true
            )

            ScrollView {
                VStack(alignment: .leading, spacing: .spacingSM) {
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(BpmDashboardStrings.whyWeTakeAnAverage)
                            .fontOpenSans(.heading4)
                            .foregroundColor(theme.textHeading)

                        Text(BpmDashboardStrings.averageExplanation)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                    }

                    BpmSummaryCardView(
                        systolic: average.systolic,
                        diastolic: average.diastolic,
                        pulse: average.pulse,
                        classification: average.classification,
                        footer: .centered(average.label)
                    )

                    if !readings.isEmpty {
                        Text(BpmDashboardStrings.lastThreeReadings)
                            .fontOpenSans(.heading4)
                            .foregroundColor(theme.textHeading)

                        VStack(spacing: .spacingSM) {
                            ForEach(readings) { reading in
                                BpmSummaryCardView(
                                    systolic: reading.systolic,
                                    diastolic: reading.diastolic,
                                    pulse: reading.pulse,
                                    classification: reading.classification,
                                    footer: .split(left: BpmDashboardStrings.mmhg, right: BpmDashboardStrings.pulse),
                                    cornerRadius: .radiusSM
                                )
                                .accessibilityElement(children: .combine)
                                .accessibilityLabel(
                                    BpmDashboardStrings.bpReadingMmhgAccessibility(
                                        systolic: reading.systolic,
                                        diastolic: reading.diastolic,
                                        pulse: reading.pulse,
                                        date: reading.formattedDate
                                    )
                                )
                            }
                        }
                    }
                }
                .padding(.spacingSM)
            }
        }
        .background(theme.backgroundSecondary)
    }
}
