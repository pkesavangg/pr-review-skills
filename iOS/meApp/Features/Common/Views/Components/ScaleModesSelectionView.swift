import SwiftUI

/// Reusable component for scale modes selection with heart rate toggle
struct ScaleModesSelectionView: View {
    @Environment(\.appTheme) private var theme
    let selectedMode: ScaleModes
    let isHeartRateEnabled: Bool
    let isR4ScaleSetup: Bool
    let onBIAButtonTap: () -> Void
    let onValueChanged: (ScaleModes, Bool) -> Void
    
    @State private var internalSelectedMode: ScaleModes
    @State private var internalIsHeartRateEnabled: Bool
    
    private let lang = ScaleModesStrings.self
    
    init(selectedMode: ScaleModes, isHeartRateEnabled: Bool, isR4ScaleSetup: Bool, onBIAButtonTap: @escaping () -> Void, onValueChanged: @escaping (ScaleModes, Bool) -> Void) {
        self.selectedMode = selectedMode
        self.isHeartRateEnabled = isHeartRateEnabled
        self.isR4ScaleSetup = isR4ScaleSetup
        self.onBIAButtonTap = onBIAButtonTap
        self.onValueChanged = onValueChanged
        self._internalSelectedMode = State(initialValue: selectedMode)
        self._internalIsHeartRateEnabled = State(initialValue: isHeartRateEnabled)
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: .spacingLG) {
            descriptionWithBIAButton
            
            SegmentedButtonView(
                segments: ScaleModes.allCases,
                selectedSegment: $internalSelectedMode
            )
            .onChange(of: internalSelectedMode) { _, newValue in
                onValueChanged(newValue, internalIsHeartRateEnabled)
            }
            
            Group {
                if internalSelectedMode == .allBodyMetrics {
                    AllBodyMetricsContentView(
                        isHeartRateOn: $internalIsHeartRateEnabled
                    ) { newValue in
                            internalIsHeartRateEnabled = newValue
                            onValueChanged(internalSelectedMode, newValue)
                        }
                } else if internalSelectedMode == .weightOnly {
                    WeightOnlyContentView()
                }
            }
            .frame(maxHeight: .infinity, alignment: .top)
        }
        .background(theme.backgroundSecondary)
        .onAppear {
            // Ensure internal state matches external state when view appears
            internalSelectedMode = selectedMode
            internalIsHeartRateEnabled = isHeartRateEnabled
        }
        .onChange(of: selectedMode) { _, newValue in
            // Update internal state when external state changes
            internalSelectedMode = newValue
        }
        .onChange(of: isHeartRateEnabled) { _, newValue in
            // Update internal state when external state changes
            internalIsHeartRateEnabled = newValue
        }
    }
    
    private var descriptionWithBIAButton: some View {
        VStack(alignment: .leading, spacing: .spacingSM) {
            if isR4ScaleSetup {
                Text(lang.changeScaleModeTitle)
                    .fontOpenSans(.heading4)
                    .fontWeight(.bold)
            }
            
            InlineButtonText(
                prefix: lang.biaExplanationPrefix,
                linkText: lang.biaButtonText,
                suffix: lang.biaExplanationSuffix
            ) {
                onBIAButtonTap()
            }
        }
        .padding(.top, .spacingMD)
    }
}

#Preview {
    ScaleModesSelectionView(
        selectedMode: .allBodyMetrics,
        isHeartRateEnabled: true,
        isR4ScaleSetup: true,
        onBIAButtonTap: {},
        onValueChanged: { _, _ in }
    )
    .environmentObject(Theme.shared)
} 
