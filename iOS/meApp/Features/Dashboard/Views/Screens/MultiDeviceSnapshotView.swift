//
//  MultiDeviceSnapshotView.swift
//  meApp
//
//  Snapshot cards container for multi-device dashboard.
//  Shows mini graphs for each available product type.
//

import SwiftUI

struct MultiDeviceSnapshotView: View {
    @ObservedObject var productTypeStore: ProductTypeStore
    @Environment(\.appTheme) private var theme
    let onSelectItem: (ProductSelection) -> Void

    var body: some View {
        VStack(spacing: .spacingSM) {
            ForEach(productTypeStore.availableItems) { item in
                switch item {
                case .myWeight:
                    WeightSnapshotCard(summaries: EntryService.shared.dailySummaries) {
                        onSelectItem(item)
                    }
                case .myBloodPressure:
                    BpmSnapshotCard(summaries: EntryService.shared.bpmDailySummaries) {
                        onSelectItem(item)
                    }
                case .baby:
                    // Baby snapshot cards will be added in baby scale phase
                    EmptyView()
                }
            }
        }
        .padding(.spacingSM)
        .onAppear {
            Task {
                await EntryService.shared.loadDashboardData(entryType: .wg)
                await EntryService.shared.loadDashboardData(entryType: .bpm)
            }
        }
    }
}
