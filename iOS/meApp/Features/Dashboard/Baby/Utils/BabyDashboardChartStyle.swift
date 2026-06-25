//
//  BabyDashboardChartStyle.swift
//  meApp
//
//  Shared visual styling for baby dashboard charts so the snapshot and
//  full trend graph stay aligned with the product mocks.
//

import SwiftUI

enum BabyDashboardChartStyle {
    static let weightColor = ColorTokens.babyScale
    static let percentileLineWidth: CGFloat = 1.0

    static func percentileLineColor(for line: BabyPercentileLine?, theme: AppColors.Palette) -> Color {
        _ = line
        return theme.statusUtilityPrimary
    }

    static func percentileLineWidth(for line: BabyPercentileLine?) -> CGFloat {
        _ = line
        return percentileLineWidth
    }
}
