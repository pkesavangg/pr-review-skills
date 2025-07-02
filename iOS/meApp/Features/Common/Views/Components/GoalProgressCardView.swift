import SwiftUI

struct GoalProgressCardView: View {
    let delta: Double
    let startWeight: Double
    let goalWeight: Double
    let unit: String
    let isRemoved: Bool
    let progress: CGFloat
    let goalType: GoalType

    let lang = DashboardStrings.self
    @Environment(\.appTheme) private var theme

    var body: some View {
        NoteBox(alignCenter: false) {
            switch goalType {
            case .lose, .gain:
                GoalView
            case .maintain:
                maintainGoalView
            }
        }
        .frame(height: 120)
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusSM)
    }

    private var GoalView: some View {
        VStack(alignment: .leading, spacing: 2) {
            DeltaHeader
            ProgressBarView(
                progress: progress,
                leftLabel: String(format: "%.1f %@", startWeight, unit),
                rightLabel: String(format: "%.1f %@", goalWeight, unit),
                progressBarColor: isRemoved ? theme.actionTertiary : theme.statusSuccess,
                labelForegroundColor: theme.textSubheading
            )
        }
        .padding(.horizontal, .spacingSM)
        .padding(.vertical, .spacingXS)
    }

    private var maintainGoalView: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            DeltaText
            Text(lang.gainGoalWeightLabel(String(goalWeight), unit))
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
                .padding(.top, 6)
        }
        .padding([.horizontal, .vertical], .spacingSM)
    }
        
    private var DeltaHeader: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            DeltaText
            Text(lang.loseGoalWeightLabel(unit))
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
                .padding(.top, 6)
        }
    }
    
    private var DeltaText: some View {
        Text(String(format: "%@%.1f", deltaPrefix, abs(delta)))
            .fontOpenSans(.heading3)
            .fontWeight(.bold)
            .foregroundColor(theme.textHeading)
    }
    
    private var deltaPrefix: String {
        switch goalType {
        case .gain: return lang.plus
        case .lose: return lang.minus
        case .maintain: return ""
        }
    }
}

#Preview {
    VStack(spacing: 20) {
        GoalProgressCardView(
            delta: -13.2,
            startWeight: 154.3,
            goalWeight: 132.3,
            unit: "lbs",
            isRemoved: false,
            progress: 0.58,
            goalType: .lose
        )
        GoalProgressCardView(
            delta: 5.0,
            startWeight: 120.0,
            goalWeight: 130.0,
            unit: "lbs",
            isRemoved: false,
            progress: 0.3,
            goalType: .gain
        )
        GoalProgressCardView(
            delta: 0.0,
            startWeight: 140.0,
            goalWeight: 140.0,
            unit: "lbs",
            isRemoved: false,
            progress: 1.0,
            goalType: .maintain
        )
    }
    .padding()
    .background(Color(.systemGroupedBackground))
}
