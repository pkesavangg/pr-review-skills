import SwiftUI

struct MetricCardView: View {
    let value: String
    let label: String
    let metricType: DashboardMetricType
    let isEditMode: Bool
    let isRemoved: Bool
    let onToggleRemoval: () -> Void
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
                Text(value)
                    .fontOpenSans(.heading4)
                    .fontWeight(.bold)
                    .foregroundColor(theme.textHeading)
            }
            Text(label)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
        }
        .frame(maxWidth: .infinity, minHeight: Self.defaultCardMinHeight)
        .padding(.vertical, verticalPadding)
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusSM)
        .editModeOverlay(
            isEditMode: isEditMode,
            isRemoved: isRemoved,
            onToggleRemoval: onToggleRemoval
        )
    }
}

#Preview {
    VStack(spacing: 16) {
        MetricCardView(value: "24.5", label: "bmi", metricType: .twelve, isEditMode: false, isRemoved: false, onToggleRemoval: {})
        MetricCardView(value: "18.3", label: "body fat",metricType: .four, isEditMode: false, isRemoved: false, onToggleRemoval: {})
        MetricCardView(value: "8", label: "visceral fat", metricType: .twelve, isEditMode: true, isRemoved: false, onToggleRemoval: {})
        MetricCardView(value: "41.6", label: "muscle", metricType: .four, isEditMode: true, isRemoved: true, onToggleRemoval: {})
    }
    .padding()
    .background(Color(.systemGroupedBackground))
}
