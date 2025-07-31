import SwiftUI

/// Stand-alone view that displays a user’s goal progress. It mirrors the
/// styling of `GoalProgressCardView` but pulls its own data via
/// `GoalProgressViewModel`, making it re-usable anywhere in the app.
struct GoalProgressView: View {

    // MARK: - State
    @StateObject private var viewModel = GoalProgressViewModel()

    // MARK: - Environment
    @Environment(\.appTheme) private var theme

    // MARK: - Constants
    private let lang = DashboardStrings.self
    // Use plural "lbs" for display when unit is imperial
    private var displayUnit: String { viewModel.unit == "lb" ? "lbs" : viewModel.unit }

    var body: some View {
        NoteBox(alignCenter: false) {
            Group {
                if viewModel.goalType == .maintain {
                    maintainGoalView
                } else {
                    goalView
                }
            }
            .padding(.leading, .spacingSM)
        }
        .frame(height: 120)
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusSM)
    }

    // MARK: - Lose / Gain goal UI
    private var goalView: some View {
        VStack(alignment: .leading, spacing: 3) {
            deltaHeader
            ProgressBarView(
                progress: viewModel.progress,
                leftLabel: formatWeight(viewModel.startWeight),
                rightLabel: formatWeight(viewModel.goalWeight),
                progressBarColor: theme.statusSuccess,
                labelForegroundColor: theme.textSubheading
            )
        }
    }

    // MARK: - Maintain goal UI (no progress bar)
    private var maintainGoalView: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            deltaText
            Text("\(displayUnit) to \(String(format: "%.1f%@", viewModel.goalWeight, displayUnit)) goal weight")
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
        }
    }

    // MARK: - Sub-components
    private var deltaHeader: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            deltaText
            Text(lang.loseGoalWeightLabel(displayUnit))
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
        }
    }

    private var deltaText: some View {
        Text(formatDeltaText())
            .fontOpenSans(.heading3)
            .foregroundColor(theme.textHeading)
    }

    // MARK: - Formatting helpers
    private func formatDeltaText() -> String {
        let prefix: String
        switch viewModel.goalType {
        case .gain:      prefix = lang.plus
        case .lose:      prefix = lang.minus
        case .maintain:  prefix = viewModel.delta >= 0 ? lang.plus : lang.minus
        }
        // Maintain goals: only number (unit will be shown in subheading)
        if viewModel.goalType == .maintain {
            return String(format: "%@%.1f", prefix, abs(viewModel.delta))
        } else {
            return String(format: "%@%.1f", prefix, abs(viewModel.delta))
        }
    }

    private func formatWeight(_ weight: Double) -> String {
        String(format: "%.1f %@", weight, displayUnit)
    }
}

#Preview {
    GoalProgressView()
        .environmentObject(AccountService.shared) // Needed for preview demo
        .padding()
}
