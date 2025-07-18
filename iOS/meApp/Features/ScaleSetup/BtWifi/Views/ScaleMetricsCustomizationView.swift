import SwiftUI

struct ScaleMetricsCustomizationView: View {
    @Environment(\.appTheme) private var theme

    /// Keys (ordered) that were previously enabled. Determines initial toggle state **and** order.
    private let initialEnabledKeys: [String]

    // MARK: - State
    @State private var bodyMetrics: [ScaleMetricSetting] = []
    @State private var progressMetrics: [ScaleMetricSetting] = []

    /// Emits ordered list of enabled metric keys whenever user toggles / reorders.
    let onSave: ([String]) -> Void

    private let lang = BtWifiScaleSetupStrings.ScaleMetricsCustomizationViewStrings.self

    init(initialEnabledKeys: [String] = ScaleMetrics.defaultMetricsKeys,
         onSave: @escaping ([String]) -> Void) {
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
                    onMove: moveBodyMetrics
                )
            }
            
            // Progress Metrics Section
            Section {
                MetricsSectionView(
                    metrics: $progressMetrics,
                    onValueChanged: saveMetrics,
                    onMove: moveProgressMetrics,
                    showIcon: false
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
        onSave(enabledMetrics)
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
        let ordering: (ScaleMetricSetting, ScaleMetricSetting) -> Bool = { a, b in
            let ia = initialEnabledKeys.firstIndex(of: a.key) ?? Int.max
            let ib = initialEnabledKeys.firstIndex(of: b.key) ?? Int.max
            return ia < ib
        }
        allBody.sort(by: ordering)
        allProgress.sort(by: ordering)

        bodyMetrics = allBody
        progressMetrics = allProgress
    }
}

#Preview {
    ScaleMetricsCustomizationView { metrics in
        print("Saved metrics:", metrics)
    }
    .environmentObject(Theme.shared)
}


// TODO: Use MetricsSectionView after Lakshmi Priya’s PR is merged, as it contains the necessary changes.
//  MetricsSectionView.swift
//  meApp
//
//  Created by AI Assistant on 26/06/25.
//

import SwiftUI

/// Reusable component for metrics sections with drag-to-reorder functionality
struct MetricsSectionView: View {
    @Environment(\.appTheme) private var theme
    
    // Required parameters
    let metrics: Binding<[ScaleMetricSetting]>
    let onValueChanged: () -> Void
    let onMove: (IndexSet, Int) -> Void
    
    // Optional parameters for customization
    let showIcon: Bool
    
    init(
        metrics: Binding<[ScaleMetricSetting]>,
        onValueChanged: @escaping () -> Void,
        onMove: @escaping (IndexSet, Int) -> Void,
        showIcon: Bool = true
    ) {
        self.metrics = metrics
        self.onValueChanged = onValueChanged
        self.onMove = onMove
        self.showIcon = showIcon
    }
    
    var body: some View {
        Section {
            ForEach(metrics) { $metric in
                ToggleListItem(
                    isOn: $metric.isEnabled,
                    text: metric.name,
                    icon: showIcon ? metric.imagePath : nil,
                    isDisabled: !metric.isEnabled
                )
                .onChange(of: metric.isEnabled) { _ in
                    onValueChanged()
                }
                .listRowBackground(theme.backgroundPrimary)
                .listRowInsets(EdgeInsets())
            }
            .onMove { indices, newOffset in
                onMove(indices, newOffset)
                onValueChanged()
            }
        }
        .listRowBackground(Color.clear)
        .listRowInsets(EdgeInsets())
    }
}

#Preview {
    MetricsSectionView(
        metrics: .constant([
            ScaleMetricSetting(name: "Body Fat", key: "bodyFat", isEnabled: true),
            ScaleMetricSetting(name: "Muscle Mass", key: "muscle", isEnabled: false)
        ]),
        onValueChanged: {},
        onMove: { _, _ in }
    )
    .environmentObject(Theme.shared)
}
