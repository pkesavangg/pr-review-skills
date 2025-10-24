//
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
    /// Optional toggle handler so parent/view-model can control reordering logic
    let onToggle: ((ScaleMetricSetting, Bool) -> Void)?
    
    // Optional parameters for customization
    let showIcon: Bool
    /// Optional predicate to decide if a particular metric's toggle should be disabled
    let shouldDisableToggle: ((ScaleMetricSetting) -> Bool)?
    
    init(
        metrics: Binding<[ScaleMetricSetting]>,
        onValueChanged: @escaping () -> Void,
        onMove: @escaping (IndexSet, Int) -> Void,
        showIcon: Bool = true,
        onToggle: ((ScaleMetricSetting, Bool) -> Void)? = nil,
        shouldDisableToggle: ((ScaleMetricSetting) -> Bool)? = nil
    ) {
        self.metrics = metrics
        self.onValueChanged = onValueChanged
        self.onMove = onMove
        self.showIcon = showIcon
        self.onToggle = onToggle
        self.shouldDisableToggle = shouldDisableToggle
    }
    
    var body: some View {
        Section {
            ForEach(metrics) { $metric in
                ToggleListItem(
                    isOn: $metric.isEnabled,
                    text: metric.name,
                    icon: showIcon ? metric.imagePath : nil,
                    isDisabled: !metric.isEnabled,
                    disableToggle: shouldDisableToggle?(metric) ?? false
                )
                .id(metric.key + (metric.isEnabled ? "-on" : "-off"))
                .onChange(of: metric.isEnabled) { _, newValue in
                    if let onToggle = onToggle {
                        onToggle(metric, newValue)
                    } else {
                        onValueChanged()
                    }
                }
                .listRowBackground(theme.backgroundPrimary)
                .listRowInsets(EdgeInsets())
                // Hide/disable the drag (reorder) indicator for disabled metrics
                .moveDisabled(!metric.isEnabled)
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