//
//  MultiDeviceSnapshotView.swift
//  meApp
//
//  Snapshot cards container for multi-device dashboard.
//  Shows mini graphs for each available product type.
//

import SwiftUI

struct MultiDeviceSnapshotView: View {
    let availableItems: [ProductSelection]
    @StateObject private var viewModel = MultiDeviceSnapshotViewModel()
    @Environment(\.appTheme) private var theme
    let onSelectItem: (ProductSelection) -> Void

    var body: some View {
        VStack(spacing: .spacingSM) {
            ForEach(availableItems) { item in
                switch item {
                case .myWeight:
                    WeightSnapshotCard(summaries: viewModel.dailySummaries) {
                        onSelectItem(item)
                    }
                case .myBloodPressure:
                    BpmSnapshotCard(summaries: viewModel.bpmDailySummaries) {
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
                await viewModel.loadSnapshots()
            }
        }
    }
}
