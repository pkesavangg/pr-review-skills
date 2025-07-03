import SwiftUI

struct MetricCardView: View {
    @Environment(\.appTheme) private var theme
    let value: String
    let label: String
    let metricType: DashboardMetricType
    let isEditMode: Bool
    let isRemoved: Bool
    let isSelected: Bool
    let onToggleRemoval: () -> Void
    let onTap: () -> Void
    let isDropTarget: Bool
    let onDrop: (String, String) -> Bool
    let onDropTargetChanged: (Bool) -> Void
    let verticalPadding: CGFloat
    static let twelveCardVerticalPadding: CGFloat = .spacingMD/2
    static let fourCardVerticalPadding: CGFloat = .spacingXS
    static let defaultCardMinHeight: CGFloat = 70
    
    private var backgroundColor: Color {
        isDropTarget ? theme.backgroundSecondary
        : (isSelected && !isEditMode ? theme.actionSecondary : theme.backgroundPrimary)
    }

    private var foregroundColor: Color {
        (isSelected && !isEditMode) ? theme.textInverse : theme.textHeading
    }

    private var subheadingColor: Color {
        (isSelected && !isEditMode) ? theme.textInverse : theme.textSubheading
    }

    var body: some View {
        content()
        .frame(maxWidth: .infinity, minHeight: Self.defaultCardMinHeight)
        .padding(.vertical, verticalPadding)
        .background(backgroundColor)
        .cornerRadius(.radiusSM)
        .overlay(
            RoundedRectangle(cornerRadius: .radiusSM)
                .strokeBorder(style: StrokeStyle(lineWidth: 2, dash: [6]))
                .foregroundColor(isDropTarget ? theme.actionSecondary : Color.clear)
        )
        .contentShape(Rectangle())
        .onTapGesture {
            onTap()
        }
    }
    
    private func content() -> some View {
        VStack(spacing: 1) {
            Text(value)
                .fontOpenSans(.heading4)
                .fontWeight(.bold)
                .foregroundColor(foregroundColor)
            Text(label)
                .fontOpenSans(.subHeading2)
                .foregroundColor(subheadingColor)
        }
    }
}

#Preview {
    VStack(spacing: 16) {
        MetricCardView(
            value: "24.5",
            label: "bmi",
            metricType: .twelve,
            isEditMode: false,
            isRemoved: false,
            isSelected: false,
            onToggleRemoval: {},
            onTap: {},
            isDropTarget: false,
            onDrop: { _, _ in false },
            onDropTargetChanged: { _ in },
            verticalPadding: MetricCardView.twelveCardVerticalPadding
        )
        MetricCardView(
            value: "18.3",
            label: "body fat",
            metricType: .four,
            isEditMode: false,
            isRemoved: false,
            isSelected: true,
            onToggleRemoval: {},
            onTap: {},
            isDropTarget: false,
            onDrop: { _, _ in false },
            onDropTargetChanged: { _ in },
            verticalPadding: MetricCardView.fourCardVerticalPadding
        )
        MetricCardView(
            value: "8",
            label: "visceral fat",
            metricType: .twelve,
            isEditMode: true,
            isRemoved: false,
            isSelected: false,
            onToggleRemoval: {},
            onTap: {},
            isDropTarget: false,
            onDrop: { _, _ in false },
            onDropTargetChanged: { _ in },
            verticalPadding: MetricCardView.twelveCardVerticalPadding
        )
        MetricCardView(
            value: "41.6",
            label: "muscle",
            metricType: .four,
            isEditMode: true,
            isRemoved: true,
            isSelected: false,
            onToggleRemoval: {},
            onTap: {},
            isDropTarget: false,
            onDrop: { _, _ in false },
            onDropTargetChanged: { _ in },
            verticalPadding: MetricCardView.fourCardVerticalPadding
        )
    }
    .padding()
    .background(Color(.systemGroupedBackground))
}
