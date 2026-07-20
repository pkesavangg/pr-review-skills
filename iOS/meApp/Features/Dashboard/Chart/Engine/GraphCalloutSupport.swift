import CoreGraphics
import Foundation

@MainActor
enum BaseGraphViewCalloutSupport {

    static func selectionDateLabel(
        for selectedDate: Date,
        usesBabyGrowthChartStyle: Bool,
        fallbackLabel: String?
    ) -> String {
        if usesBabyGrowthChartStyle {
            return BabyPercentileGrowthReference.formatChartSelectionDate(selectedDate).lowercased()
        }

        return (fallbackLabel ?? "").lowercased()
    }

    static func selectionValue(
        for selectedDate: Date,
        plottedPoints: [String: [PlottedGraphSeries]],
        babySelectionPresentation: BabyGraphSelectionPresentation?,
        plotXDate: (Date) -> Date,
        fallbackDisplayWeight: Double?
    ) -> Double? {
        let plottedDate = plotXDate(selectedDate)

        if let babySelectionPresentation,
           plotXDate(babySelectionPresentation.crosshairDate) == plottedDate {
            return babySelectionPresentation.crosshairValue
        }

        let matchingValues = plottedPoints.values
            .flatMap { $0 }
            .filter { $0.xDate == plottedDate }
            .map(\.original.value)

        return matchingValues.max() ?? fallbackDisplayWeight
    }

    static func selectionXPosition(
        chartX: CGFloat,
        chartWidth: CGFloat,
        isScrollable: Bool
    ) -> CGFloat {
        let baseOffset: CGFloat = chartX < chartWidth / 2 ? -10 : -40
        let maxWidth = chartWidth - (isScrollable ? 100 : 85)
        return max(40, min(maxWidth, chartX + baseOffset))
    }

    static func percentileTopPadding(for chartY: CGFloat) -> CGFloat {
        max(0, chartY - 22)
    }

    static func goalWeightLabel(
        roundedValue: Double,
        formattedValue: String?,
        fallbackFormatter: (Double) -> String
    ) -> String {
        formattedValue ?? fallbackFormatter(roundedValue)
    }
}
