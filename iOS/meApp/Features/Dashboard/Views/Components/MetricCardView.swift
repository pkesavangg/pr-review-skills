import SwiftUI

struct MetricCardView: View {
    let value: String
    let label: String
    let unit: String?
    let preLabel: String?
    let metricType: DashboardMetricType
    @Environment(\.appTheme) private var theme

    static let twelveCardVerticalPadding: CGFloat = .spacingMD/2
    static let fourCardVerticalPadding: CGFloat = .spacingXS
    static let defaultCardMinHeight: CGFloat = 70

    var verticalPadding: CGFloat {
        switch metricType {
        case .twelve: return Self.twelveCardVerticalPadding
        case .four:   return Self.fourCardVerticalPadding
        }
    }

    var body: some View {
        VStack(spacing: 1) {
            HStack(spacing: 2) {
                if let preLabel = preLabel {
                    Text(preLabel)
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.textSubheading)
                }
                Text(value)
                    .fontOpenSans(.heading4)
                    .fontWeight(.bold)
                    .foregroundColor(theme.textHeading)
                if let unit = unit {
                    Text(unit)
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.textSubheading)
                }
            }
            Text(label)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
        }
        .frame(maxWidth: .infinity, minHeight: Self.defaultCardMinHeight)
        .padding(.vertical, verticalPadding)
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusSM)
    }
}

#Preview {
    VStack(spacing: 16) {
        MetricCardView(value: "24.5", label: "bmi", unit: nil, preLabel: nil,  metricType: .twelve)
        MetricCardView(value: "18.3", label: "body fat", unit: "%", preLabel: nil, metricType: .four)
        MetricCardView(value: "8", label: "visceral fat", unit: nil, preLabel: "Level", metricType: .twelve)
        MetricCardView(value: "1 day", label: "current streak", unit: nil, preLabel: nil, metricType: .four)
    }
    .padding()
    .background(Color(.systemGroupedBackground))
}
