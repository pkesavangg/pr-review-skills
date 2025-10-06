import SwiftUI

struct MetricCardView: View {
    @Environment(\.appTheme) private var theme
    let value: String
    let label: String
    let icon: String?
    let dashboardType: DashboardType
    let isEditMode: Bool
    let isRemoved: Bool
    let isSelected: Bool
    let onToggleRemoval: () -> Void
    let onTap: () -> Void
    let isDropTarget: Bool
    let onDrop: (String, String) -> Bool
    let onDropTargetChanged: (Bool) -> Void
    let verticalPadding: CGFloat
    let parentView: DashboardMetricsParentView
    static let twelveCardVerticalPadding: CGFloat = .spacingMD/2
    static let fourCardVerticalPadding: CGFloat = .spacingXS
    static let defaultCardMinHeight: CGFloat = 70
    
    init(
        value: String,
        label: String,
        icon: String? = nil,
        dashboardType: DashboardType,
        isEditMode: Bool,
        isRemoved: Bool,
        isSelected: Bool,
        onToggleRemoval: @escaping () -> Void,
        onTap: @escaping () -> Void,
        isDropTarget: Bool,
        onDrop: @escaping (String, String) -> Bool,
        onDropTargetChanged: @escaping (Bool) -> Void,
        verticalPadding: CGFloat,
        parentView: DashboardMetricsParentView
    ) {
        self.value = value
        self.label = label
        self.icon = icon
        self.dashboardType = dashboardType
        self.isEditMode = isEditMode
        self.isRemoved = isRemoved
        self.isSelected = isSelected
        self.onToggleRemoval = onToggleRemoval
        self.onTap = onTap
        self.isDropTarget = isDropTarget
        self.onDrop = onDrop
        self.onDropTargetChanged = onDropTargetChanged
        self.verticalPadding = verticalPadding
        self.parentView = parentView
    }
    
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
    
    private var borderColor: Color {
        isDropTarget ? theme.actionSecondary : Color.clear
    }
    
    private var borderWidth: CGFloat {
        isDropTarget ? 2 : 0
    }
    
    /// Returns the appropriate label based on parentView context
    private var displayLabel: String {
        if parentView == .R4ScaleSetup {
            return getR4ScaleSetupLabel(for: label)
        }
        return label
    }
    
    private func getR4ScaleSetupLabel(for originalLabel: String) -> String {
        switch originalLabel {
        case DashboardStrings.bmi:
            return DashboardStrings.bmi
        case DashboardStrings.bodyFat:
            return DashboardStrings.bodyFatBase
        case DashboardStrings.muscle:
            return DashboardStrings.muscleBase
        case DashboardStrings.water:
            return DashboardStrings.waterBase
        case DashboardStrings.heartBpm:
            return DashboardStrings.heartBase
        case DashboardStrings.bone:
            return DashboardStrings.boneBase
        case DashboardStrings.visceralFat:
            return DashboardStrings.visceralFat
        case DashboardStrings.subFat:
            return DashboardStrings.subFatBase
        case DashboardStrings.protein:
            return DashboardStrings.proteinBase
        case DashboardStrings.skelMuscle:
            return DashboardStrings.skelMuscle
        case DashboardStrings.bmrKcal:
            return DashboardStrings.bmrBase
        case DashboardStrings.metAge:
            return DashboardStrings.metAgeBase
        default:
            return originalLabel
        }
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
            Group {
                if parentView == .R4ScaleSetup, isEditMode, let icon, !icon.isEmpty {
                    AppIconView(icon: icon, size: IconSize())
                        .foregroundColor(foregroundColor)
                } else {
                    Text(value)
                        .fontOpenSans(.heading4)
                        .fontWeight(.bold)
                        .foregroundColor(foregroundColor)
                }
            }
            
            Text(displayLabel)
                .fontOpenSans(.subHeading2)
                .foregroundColor(subheadingColor)
                .multilineTextAlignment(parentView == .R4ScaleSetup ? .center : .leading)
        }
    }
}

#Preview {
    VStack(spacing: 16) {
        // Regular dashboard view
        MetricCardView(
            value: "24.5",
            label: "bmi",
            icon: AppAssets.bmiIcon,
            dashboardType: .dashboard12,
            isEditMode: false,
            isRemoved: false,
            isSelected: false,
            onToggleRemoval: {},
            onTap: {},
            isDropTarget: false,
            onDrop: { _, _ in false },
            onDropTargetChanged: { _ in },
            verticalPadding: MetricCardView.twelveCardVerticalPadding,
            parentView: .dashboard
        )

        MetricCardView(
            value: "24.5",
            label: DashboardStrings.bmi,
            icon: AppAssets.bmiIcon,
            dashboardType: .dashboard12,
            isEditMode: false,
            isRemoved: false,
            isSelected: false,
            onToggleRemoval: {},
            onTap: {},
            isDropTarget: false,
            onDrop: { _, _ in false },
            onDropTargetChanged: { _ in },
            verticalPadding: MetricCardView.twelveCardVerticalPadding,
            parentView: .R4ScaleSetup
        )
        
        MetricCardView(
            value: "18.3",
            label: DashboardStrings.bodyFat,
            icon: AppAssets.bodyFatIcon,
            dashboardType: .dashboard4,
            isEditMode: false,
            isRemoved: false,
            isSelected: true,
            onToggleRemoval: {},
            onTap: {},
            isDropTarget: false,
            onDrop: { _, _ in false },
            onDropTargetChanged: { _ in },
            verticalPadding: MetricCardView.fourCardVerticalPadding,
            parentView: .R4ScaleSetup
        )
        
        MetricCardView(
            value: "1,800",
            label: DashboardStrings.bmrKcal,
            icon: AppAssets.bmrIcon,
            dashboardType: .dashboard12,
            isEditMode: false,
            isRemoved: false,
            isSelected: false,
            onToggleRemoval: {},
            onTap: {},
            isDropTarget: false,
            onDrop: { _, _ in false },
            onDropTargetChanged: { _ in },
            verticalPadding: MetricCardView.twelveCardVerticalPadding,
            parentView: .R4ScaleSetup
        )
        
        // Edit mode with wiggle animation (matching movingGridsLearning exactly)
        // Row 0 - even timing (0.135s)
        MetricCardView(
            value: "22.1",
            label: "muscle mass",
            icon: AppAssets.muscleIcon,
            dashboardType: .dashboard12,
            isEditMode: true,
            isRemoved: false,
            isSelected: false,
            onToggleRemoval: {},
            onTap: {},
            isDropTarget: false,
            onDrop: { _, _ in false },
            onDropTargetChanged: { _ in },
            verticalPadding: MetricCardView.twelveCardVerticalPadding,
            parentView: .dashboard
        )
        .wiggling(true, rowIndex: 0) // Even row timing
        
        // Row 1 - odd timing (0.125s)
        MetricCardView(
            value: "15.2",
            label: "water weight",
            icon: AppAssets.waterIcon,
            dashboardType: .dashboard4,
            isEditMode: true,
            isRemoved: false,
            isSelected: false,
            onToggleRemoval: {},
            onTap: {},
            isDropTarget: false,
            onDrop: { _, _ in false },
            onDropTargetChanged: { _ in },
            verticalPadding: MetricCardView.fourCardVerticalPadding,
            parentView: .dashboard
        )
        .wiggling(true, rowIndex: 1) // Odd row timing
        
        // Row 2 - even timing (0.135s)
        MetricCardView(
            value: "28.7",
            label: "bone mass",
            icon: AppAssets.boneIcon,
            dashboardType: .dashboard12,
            isEditMode: true,
            isRemoved: false,
            isSelected: false,
            onToggleRemoval: {},
            onTap: {},
            isDropTarget: false,
            onDrop: { _, _ in false },
            onDropTargetChanged: { _ in },
            verticalPadding: MetricCardView.twelveCardVerticalPadding,
            parentView: .dashboard
        )
        .wiggling(true, rowIndex: 2) // Even row timing
        
        // Edit mode - removed item (no wiggle)
        MetricCardView(
            value: "15.2",
            label: "water weight",
            icon: AppAssets.waterIcon,
            dashboardType: .dashboard4,
            isEditMode: true,
            isRemoved: true,
            isSelected: false,
            onToggleRemoval: {},
            onTap: {},
            isDropTarget: false,
            onDrop: { _, _ in false },
            onDropTargetChanged: { _ in },
            verticalPadding: MetricCardView.fourCardVerticalPadding,
            parentView: .dashboard
        )
    }
    .padding()
    .environmentObject(Theme.shared)
}
