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
                canShowPresentationIndicator: true,
                leadingAccessibilityID: AccessibilityID.ahaRatingCloseButton
            )

            ScrollView {
                VStack(alignment: .leading, spacing: .spacingSM) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(BpmDashboardStrings.bloodPressureLevelColors)
                            .fontOpenSans(.heading4)
                            .foregroundColor(theme.textHeading)

                        Text(BpmDashboardStrings.colorChartDescription)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                    }

                    ForEach(AhaPressureClass.allCases.reversed()) { classification in
                        classificationRow(classification)
                    }
                }
                .padding(.spacingSM)
            }
        }
        .background(theme.backgroundSecondary)
        .screenAccessibilityRoot(AccessibilityID.ahaRatingSheetRoot)
    }

    @ViewBuilder
    private func classificationRow(_ classification: AhaPressureClass) -> some View {
        HStack(alignment: .center, spacing: 20) {
            RoundedRectangle(cornerRadius: 4)
                .fill(classification.color(theme: theme))
                .frame(width: 27, height: 65)

            VStack(alignment: .leading, spacing: .zero) {
                Text(classification.label)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)

                let rangeText = "\(BpmDashboardStrings.systolicRangePrefix)\(classification.systolicRange)"
                    + "\(BpmDashboardStrings.diastolicRangePrefix)\(classification.diastolicRange)"
                Text(rangeText)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)
            }

            Spacer()
        }
    }
}
