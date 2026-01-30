import SwiftUI

struct ScaleMetricsCustomizationView: View {
    @Environment(\.appTheme) private var theme

    /// Keys (ordered) that were previously enabled. Determines initial toggle state **and** order.
    private let initialEnabledKeys: [String]

    // MARK: - State
    @State private var bodyMetrics: [ScaleMetricSetting] = []
    @State private var progressMetrics: [ScaleMetricSetting] = []

    /// Emits ordered list of enabled metric keys and whether it differs from the initial keys
    /// whenever user toggles / reorders.
    let onSave: ([String], Bool) -> Void

    private let lang = BtWifiScaleSetupStrings.ScaleMetricsCustomizationViewStrings.self

    init(initialEnabledKeys: [String] = ScaleMetrics.defaultMetricsKeys,
         onSave: @escaping ([String], Bool) -> Void) {
        self.initialEnabledKeys = initialEnabledKeys
        self.onSave = onSave

        // _bodyMetrics & _progressMetrics will be assigned later in onAppear.
    }

    var body: some View {
        List {
            // Body Metrics Section
            descriptionSection()
            Section {
                MetricsSectionView(
                    metrics: $bodyMetrics,
                    onValueChanged: saveMetrics,
                    onMove: moveBodyMetrics,
                    onToggle: handleBodyMetricToggle
                )
            }
            
            // Progress Metrics Section
            Section {
                MetricsSectionView(
                    metrics: $progressMetrics,
                    onValueChanged: saveMetrics,
                    onMove: moveProgressMetrics,
                    showIcon: false,
                    onToggle: handleProgressMetricToggle
                )
            }
        }
        .scrollContentBackground(.hidden)
        .listStyle(.insetGrouped)
        .scrollIndicators(.hidden)
        .environment(\.editMode, .constant(.active))
        .onAppear(perform: configureInitialState)
    }

    private func moveBodyMetrics(from source: IndexSet, to destination: Int) {
        bodyMetrics.move(fromOffsets: source, toOffset: destination)
    }
    
    private func moveProgressMetrics(from source: IndexSet, to destination: Int) {
        progressMetrics.move(fromOffsets: source, toOffset: destination)
    }
    
    private func saveMetrics() {
        let enabledMetrics = bodyMetrics.filter { $0.isEnabled }.map { $0.key } +
                           progressMetrics.filter { $0.isEnabled }.map { $0.key }
        let hasChanged = enabledMetrics != initialEnabledKeys
        onSave(enabledMetrics, hasChanged)
    }
    
    private func descriptionSection() -> some View {
        Section {
            VStack(alignment: .leading, spacing: .spacingXS) {
                Text(lang.title)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(lang.subtitle)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

        }
        .listRowBackground(Color.clear)
        .listRowInsets(EdgeInsets())
    }

    // MARK: - Helpers

    /// Configure toggle states and order based on `initialEnabledKeys` once when the view appears.
    private func configureInitialState() {
        // Prepare metrics arrays with correct enabled state first.
        var allBody = ScaleMetrics.bodyMetrics
        var allProgress = ScaleMetrics.progressMetrics

        for index in allBody.indices {
            allBody[index].isEnabled = initialEnabledKeys.contains(allBody[index].key)
        }
        for index in allProgress.indices {
            allProgress[index].isEnabled = initialEnabledKeys.contains(allProgress[index].key)
        }

        // Re-order according to saved order but keep any new metrics at end in default order.
        let ordering: (ScaleMetricSetting, ScaleMetricSetting) -> Bool = { first, second in
            let firstIndex = initialEnabledKeys.firstIndex(of: first.key) ?? Int.max
            let secondIndex = initialEnabledKeys.firstIndex(of: second.key) ?? Int.max
            return firstIndex < secondIndex
        }
        allBody.sort(by: ordering)
        allProgress.sort(by: ordering)

        bodyMetrics = allBody
        progressMetrics = allProgress
    }

    /// Handles metric toggle and reorders the list to move toggled item to end of its group
    private func handleBodyMetricToggle(metric: ScaleMetricSetting, isEnabled: Bool) {
        // Update toggle state immediately so SwiftUI can re-evaluate .moveDisabled()
        if let idx = bodyMetrics.firstIndex(where: { $0.key == metric.key }) {
            bodyMetrics[idx].isEnabled = isEnabled
        }
        saveMetrics()
        
        // Reorder on next run loop to ensure .moveDisabled() is updated first
        DispatchQueue.main.async {
            withAnimation {
                self.bodyMetrics = ScaleMetricSetting.reorderOnToggle(items: self.bodyMetrics, key: metric.key, isEnabled: isEnabled)
            }
        }
    }
    
    /// Handles progress metric toggle and reorders the list
    private func handleProgressMetricToggle(metric: ScaleMetricSetting, isEnabled: Bool) {
        // Update toggle state immediately so SwiftUI can re-evaluate .moveDisabled()
        if let idx = progressMetrics.firstIndex(where: { $0.key == metric.key }) {
            progressMetrics[idx].isEnabled = isEnabled
        }
        saveMetrics()
        
        // Reorder on next run loop to ensure .moveDisabled() is updated first
        DispatchQueue.main.async {
            withAnimation {
                self.progressMetrics = ScaleMetricSetting.reorderOnToggle(items: self.progressMetrics, key: metric.key, isEnabled: isEnabled)
            }
        }
    }
}

#Preview {
    ScaleMetricsCustomizationView { metrics,arg  in
        print("Saved metrics:", metrics)
    }
    .environmentObject(Theme.shared)
}
