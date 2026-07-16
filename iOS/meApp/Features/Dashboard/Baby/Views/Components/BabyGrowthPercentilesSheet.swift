//
//  BabyGrowthPercentilesSheet.swift
//  meApp
//

import SwiftUI

struct BabyGrowthPercentilesSheet: View {
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    let state: BabyGrowthPercentilesSheetState

    private let accentColor = BabyDashboardChartStyle.weightColor

    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView(
                title: BabyDashboardStrings.growthPercentilesTitle,
                leadingContent: {
                    AppIconView(icon: AppAssets.close, size: IconSize(width: 16, height: 16))
                },
                trailingContent: {
                    EmptyView()
                },
                onLeadingTap: {
                    dismiss()
                },
                canShowBorder: true,
                canShowPresentationIndicator: true,
                leadingAccessibilityID: AccessibilityID.babyGrowthPercentilesCloseButton
            )

            VStack(alignment: .leading, spacing: .spacingLG) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(BabyDashboardStrings.growthPercentilesHeading)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)

                    Text(BabyDashboardStrings.growthPercentilesDescription)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                        .fixedSize(horizontal: false, vertical: true)
                }

                heightCard
                weightCard

                Spacer(minLength: .spacingXL)
            }
            .padding(.horizontal, .spacingSM)
            .padding(.top, .spacingLG)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .background(theme.backgroundSecondary)
        .screenAccessibilityRoot(AccessibilityID.babyGrowthPercentilesSheetRoot)
    }

    private var heightCard: some View {
        percentileCard {
            HStack(alignment: .center, spacing: .spacingSM) {
                HStack(alignment: .lastTextBaseline, spacing: 5) {
                    Text(state.heightDisplayText)
                        .fontOpenSans(.heading2)
                        .foregroundColor(accentColor)

                    Text(BabyDashboardStrings.inches)
                        .fontOpenSans(.subHeading1)
                        .foregroundColor(theme.textSubheading)
                }

                Spacer(minLength: .spacingMD)

                percentileValue(state.heightPercentileText)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Height \(state.heightDisplayText) inches, \(state.heightPercentileText) percent")
    }

    private var weightCard: some View {
        // CDC Growth Percentiles — weight row (Figma node 26501:378218)
        let display = state.weightDisplay
        return HStack(alignment: .lastTextBaseline, spacing: 0) {
            HStack(alignment: .lastTextBaseline, spacing: 0) {
                valueUnitPair(value: display.primary, unit: display.primaryUnit)
                if let secondary = display.secondary, let secondaryUnit = display.secondaryUnit {
                    valueUnitPair(value: secondary, unit: secondaryUnit)
                }
            }

            Spacer(minLength: Self.weightCardWeightToPercentileSpacing)

            weightPercentileCluster(state.weightPercentileText)
        }
        .lineLimit(1)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, Self.weightCardHorizontalPadding)
        .padding(.vertical, .spacingXSM)
        .background(theme.backgroundPrimary)
        .clipShape(RoundedRectangle(cornerRadius: Self.weightCardCornerRadius, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityLabel({
            let weightText = display.secondary != nil
                ? "\(display.primary) \(display.primaryUnit) \(display.secondary ?? "") \(display.secondaryUnit ?? "")"
                : "\(display.primary) \(display.primaryUnit)"
            return "Weight \(weightText), \(state.weightPercentileText) percent"
        }())
    }

    private func percentileCard<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        content()
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: 97, alignment: .leading)
            .background(theme.backgroundPrimary)
            .cornerRadius(.radiusSM)
    }

    private func valueUnitPair(value: String, unit: String) -> some View {
        HStack(alignment: .lastTextBaseline, spacing: 5) {
            Text(value)
                .fontOpenSans(.heading2)
                .foregroundColor(accentColor)
                .lineLimit(1)
                .minimumScaleFactor(Self.weightCardMinimumScaleFactor)
                .allowsTightening(true)

            Text(unit)
                .fontOpenSans(.subHeading1)
                .foregroundColor(theme.textSubheading)
                .lineLimit(1)
                .minimumScaleFactor(Self.weightCardMinimumScaleFactor)
        }
    }

    private func percentileValue(_ value: String) -> some View {
        HStack(alignment: .lastTextBaseline, spacing: 2) {
            Text(value)
                .fontOpenSans(.heading2)
                .foregroundColor(theme.textHeading.opacity(0.72))

            Text(BabyDashboardStrings.percentSymbol)
                .fontOpenSans(.subHeading1)
                .foregroundColor(theme.textSubheading)
        }
    }

    /// Percentile column for the weight card: large value + “%” both use subheading tone (Figma 26501:378227).
    private func weightPercentileCluster(_ value: String) -> some View {
        HStack(alignment: .lastTextBaseline, spacing: 2) {
            Text(value)
                .fontOpenSans(.heading2)
                .foregroundColor(theme.textSubheading)
                .lineLimit(1)
                .minimumScaleFactor(Self.weightCardMinimumScaleFactor)
                .allowsTightening(true)

            Text(BabyDashboardStrings.percentSymbol)
                .fontOpenSans(.subHeading1)
                .foregroundColor(theme.textSubheading)
                .lineLimit(1)
                .minimumScaleFactor(Self.weightCardMinimumScaleFactor)
        }
    }

    private static let weightCardHorizontalPadding: CGFloat = 24
    private static let weightCardCornerRadius: CGFloat = 9
    private static let weightCardWeightToPercentileSpacing: CGFloat = 12
    private static let weightCardMinimumScaleFactor: CGFloat = 0.72
}
