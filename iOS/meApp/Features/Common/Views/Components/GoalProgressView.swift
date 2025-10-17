import SwiftUI

/// Stand-alone view that displays a user’s goal progress. It mirrors the
/// styling of `GoalProgressCardView` but pulls its own data via
/// `GoalProgressViewModel`, making it re-usable anywhere in the app.
struct GoalProgressView: View {

    // MARK: - State
    @StateObject private var viewModel = GoalProgressViewModel()

    // MARK: - Environment
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var tabViewModel: BottomTabBarViewModel
    
    // MARK: - Constants
    private let lang = DashboardStrings.self
    // Dynamic unit label (lb/lbs or kg) based on a given display value
    private var weightUnit: WeightUnit { viewModel.unit == "kg" ? .kg : .lb }
    private func unitFor(value: Double) -> String {
        WeightValueConvertor.unitForDisplay(value: value, unit: weightUnit)
    }
    // Goal completion flag
    private var isGoalReached: Bool { viewModel.progress >= 1 }


    var body: some View {
        NoteBox(alignCenter: false) {
            Group {
                if !viewModel.isLoaded {
                    // Keep last UI while loading to avoid flicker
                    EmptyView()
                } else if viewModel.goalType == .none {
                    noGoalSetView
                } else if viewModel.goalType == .maintain {
                    maintainGoalView
                } else {
                    goalView
                }
            }
        }
        .padding(.horizontal, .spacingXS)
        .frame(height: 120)
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusSM)
    }
    
    // MARK: No Goal Set UI
    private var noGoalSetView: some View {
        HStack {
            Spacer()
            
            VStack(alignment: .center, spacing: 6){
                Text(lang.reachYourGoals)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                    .padding(.top, .spacingMD)
                
                ButtonView(text: lang.setGoalWeight, type: .filledPrimary, size: .large, isDisabled: false, backgroundColorOverride: theme.statusSuccess,  action: { tabViewModel.navigateToGoalSetting()
                })
                .padding(.bottom, .spacingMD)

            }
                        
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
       
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
            Text("\(unitFor(value: abs(viewModel.delta))) to \(formatGoalWeight(viewModel.goalWeight)) goal weight")
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
        }
    }

    // MARK: - Sub-components
    @ViewBuilder
    private var deltaHeader: some View {
        HStack(alignment: .firstTextBaseline) {
            if isGoalReached {
                Text("0")
                    .fontOpenSans(.heading3)
                    .foregroundColor(theme.textHeading)
                Text("\(unitFor(value: 0)) to goal")
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
                Spacer()
                Text(lang.goalReached)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
            } else {
                deltaText
                Text(lang.loseGoalWeightLabel(unitFor(value: abs(viewModel.delta))))
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
            }
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
        case .none:      prefix = ""
        case .gain:      prefix = lang.plus
        case .lose:      prefix = lang.minus
        case .maintain:  prefix = viewModel.delta >= 0 ? lang.plus : lang.minus
        }
        // Maintain goals: only number (unit will be shown in subheading)
        if viewModel.goalType == .maintain || viewModel.goalType == .none {
            return String(format: "%@%.1f", prefix, abs(viewModel.delta))
        } else {
            return String(format: "%@%.1f", prefix, abs(viewModel.delta))
        }
    }

    private func formatWeight(_ weight: Double) -> String {
        // In weightless mode, prepend '+' for positive offsets so user can see direction.
        if viewModel.weightlessOn && weight > 0 {
            return String(format: "+%.1f %@", weight, unitFor(value: weight))
        } else {
            return String(format: "%.1f %@", weight, unitFor(value: weight))
        }
    }

    private func formatGoalWeight(_ weight: Double) -> String {
        String(format: "%.1f %@", weight, unitFor(value: weight))
    }
}

#Preview {
    GoalProgressView()
        .environmentObject(AccountService.shared) // Needed for preview demo
        .padding()
}
