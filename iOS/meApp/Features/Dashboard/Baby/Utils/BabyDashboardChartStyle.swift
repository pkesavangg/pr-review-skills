//
//  BabyDashboardChartStyle.swift
//  meApp
//
//  Shared visual styling for baby dashboard charts so the snapshot and
//  full trend graph stay aligned with the product mocks.
//

import SwiftUI

enum BabyDashboardChartStyle {
    static let weightColor = Color(red: 0x88 / 255.0, green: 0x41 / 255.0, blue: 0xA4 / 255.0)
    static let percentileLineWidth: CGFloat = 1.0

    static func percentileLineColor(for line: BabyPercentileLine?, theme: AppColors.Palette) -> Color {
        _ = (line, theme)
        return Color.gray.opacity(0.5)
    }

    static func percentileLineWidth(for line: BabyPercentileLine?) -> CGFloat {
        _ = line
        return percentileLineWidth
    }
}
