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