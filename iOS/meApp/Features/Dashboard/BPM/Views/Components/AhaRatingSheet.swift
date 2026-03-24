//
//  AhaRatingSheet.swift
//  meApp
//
//  Modal showing AHA blood pressure classification chart.
//

import SwiftUI

struct AhaRatingSheet: View {
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView<AnyView, EmptyView>(
                title: BpmDashboardStrings.ahaRatings,
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
                    Text(BpmDashboardStrings.bloodPressureLevelColors)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)

                    Text(BpmDashboardStrings.colorChartDescription)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)

                    ForEach(AhaPressureClass.allCases.reversed()) { classification in
                        classificationRow(classification)
                    }
                }
                .padding(.spacingSM)
            }
        }
        .background(theme.backgroundSecondary)
    }

    @ViewBuilder
    private func classificationRow(_ classification: AhaPressureClass) -> some View {
        HStack(alignment: .top, spacing: .spacingSM) {
            RoundedRectangle(cornerRadius: 4)
                .fill(classification.color(theme: theme))
                .frame(width: 16, height: 48)

            VStack(alignment: .leading, spacing: 2) {
                Text(classification.label)
                    .fontOpenSans(.heading5)
                    .fontWeight(.bold)
                    .foregroundColor(theme.textHeading)

                Text("Systolic: \(classification.systolicRange)")
                    .fontOpenSans(.body3)
                    .foregroundColor(theme.textBody)

                Text("Diastolic: \(classification.diastolicRange)")
                    .fontOpenSans(.body3)
                    .foregroundColor(theme.textBody)
            }

            Spacer()
        }
    }
}
