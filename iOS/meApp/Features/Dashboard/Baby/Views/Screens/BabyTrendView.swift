//
//  BabyTrendView.swift
//  meApp
//
//  Baby-specific trend view displayed when a baby profile is selected.
//  Shows weight graph with baby-themed purple color.
//

import SwiftUI

struct BabyTrendView: View {
    @ObservedObject var dashboardStore: DashboardStore
    let babyProfile: BabyProfile
    @Environment(\.appTheme) private var theme

    // TODO: Replace with ColorTokens.babyPrimary once color tokens are updated
    private let babyColor = Color(red: 0x88 / 255.0, green: 0x41 / 255.0, blue: 0xA4 / 255.0)

    var body: some View {
        DashboardTrendView(dashboardStore: dashboardStore) {
            babyInfoSection
        }
    }

    @ViewBuilder
    private var babyInfoSection: some View {
        VStack(alignment: .leading, spacing: .zero) {
            Text(dashboardStore.displayManager.weightDisplayLabel)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
                .padding(.leading, .spacingSM)

            WeightDisplayView(
                weightText: {
                    if let displayWeight = dashboardStore.displayManager.displayWeight {
                        if abs(displayWeight) < AppConstants.Precision.doubleEqualityEpsilon {
                            return "000.0"
                        }
                        return dashboardStore.displayManager.formatWeightDisplayText(displayWeight)
                    }
                    let averageWeight = dashboardStore.displayManager.getCurrentAverageWeight()
                    if abs(averageWeight) < AppConstants.Precision.doubleEqualityEpsilon {
                        return "000.0"
                    }
                    return dashboardStore.displayManager.formatWeightDisplayText(averageWeight)
                }(),
                unitText: dashboardStore.displayManager.displayUnitText,
                weightColor: babyColor
            )
        }
    }
}
