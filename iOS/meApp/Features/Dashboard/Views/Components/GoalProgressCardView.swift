import SwiftUI

struct GoalProgressCardView: View {
    let delta: Double // e.g., -13.2
    let startWeight: Double // e.g., 154.3
    let goalWeight: Double // e.g., 132.3
    let unit: String // e.g., "lbs"
    
    @Environment(\.appTheme) private var theme
    
    var progress: CGFloat {
        // Clamp progress between 0 and 1
        let total = abs(startWeight - goalWeight)
        guard total > 0 else { return 1.0 }
        let achieved = abs(startWeight - (startWeight + delta))
        return min(max(CGFloat(achieved / total), 0), 1)
    }
    
    var body: some View {
        NoteBox(alignCenter: false) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(String(format: "%@%.1f", delta < 0 ? "" : "+", delta))
                        .fontOpenSans(.heading3)
                        .fontWeight(.bold)
                        .foregroundColor(theme.textHeading)
                    Text("\(unit) to goal")
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.textSubheading)
                        .padding(.top, 6)
                }
                ProgressBarView(
                    progress: progress,
                    leftLabel: String(format: "%.1f %@", startWeight, unit),
                    rightLabel: String(format: "%.1f %@", goalWeight, unit),
                    progressBarColor: theme.statusSuccess,
                    labelForegroundColor: theme.textSubheading
                )
            }
            .padding(.horizontal, .spacingSM)
            .padding(.vertical, .spacingXS)
        }
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusSM)
    }
}

#Preview {
    GoalProgressCardView(delta: -13.2, startWeight: 154.3, goalWeight: 132.3, unit: "lbs")
        .padding()
        .background(Color(.systemGroupedBackground))
} 
