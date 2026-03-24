//
//  ThreeReadingAverageSheet.swift
//  meApp
//
//  Detail sheet: "Why We Take an Average" + last 3 reading rows.
//

import SwiftUI

struct ThreeReadingAverageSheet: View {
    let average: ThreeReadingAverage
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView<AnyView, EmptyView>(
                title: BpmDashboardStrings.threeEntryAverage.capitalized,
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
                VStack(alignment: .leading, spacing: .spacingLG) {
                    Text("Why We Take an Average")
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)

                    Text("Blood pressure readings can vary throughout the day. Averaging your most recent readings gives a more accurate picture of your overall blood pressure health.")
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)

                    // Average display
                    HStack(spacing: .spacingSM) {
                        Text("\(average.systolic)/\(average.diastolic)")
                            .fontOpenSans(.heading2)
                            .foregroundColor(average.classification.color(theme: theme))

                        VStack(alignment: .leading) {
                            Text(average.classification.label)
                                .fontOpenSans(.body2)
                                .fontWeight(.bold)
                                .foregroundColor(average.classification.color(theme: theme))
                            Text(average.label)
                                .fontOpenSans(.body3)
                                .foregroundColor(theme.textSubheading)
                        }
                    }
                    .padding(.spacingSM)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(theme.backgroundPrimaryDisabled)
                    .cornerRadius(12)
                }
                .padding(.spacingSM)
            }
        }
        .background(theme.backgroundSecondary)
    }
}
