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
    let onSelectItem: (ProductSelection) -> Void

    var body: some View {
        let snapshotItems = viewModel.snapshotItems(from: availableItems)

        VStack(spacing: .spacingSM) {
            ForEach(snapshotItems) { item in
                if viewModel.isSnapshotReady(item) || viewModel.hasLoadedSnapshots(for: availableItems) {
                    snapshotCard(for: item)
                } else {
                    skeletonCard(for: item)
                }
            }
        }
        .padding(.spacingSM)
        .task(id: snapshotTaskID) {
            await viewModel.loadSnapshots(availableItems: availableItems)
        }
    }

    /// Stable task ID — only re-runs when the set of available items changes.
    private var snapshotTaskID: Int {
        var hasher = Hasher()
        for item in availableItems {
            hasher.combine(item)
        }
        return hasher.finalize()
    }

    // MARK: - Skeleton Placeholder

    private var snapshotSkeletonCards: some View {
        VStack(spacing: .spacingSM) {
            ForEach(Array(availableItems.prefix(3).enumerated()), id: \.offset) { _, item in
                skeletonCard(for: item)
            }
        }
    }

    @ViewBuilder
    private func snapshotCard(for item: ProductSelection) -> some View {
        switch item {
        case .myWeight:
            WeightSnapshotCard(summaries: viewModel.dailySummaries) {
                onSelectItem(item)
            }
            .accessibilityIdentifier(AccessibilityID.weightCard)
        case .myBloodPressure:
            BpmSnapshotCard(summaries: viewModel.bpmDailySummaries) {
                onSelectItem(item)
            }
            .accessibilityIdentifier(AccessibilityID.bpCard)
        case .baby(let profile):
            BabySnapshotCard(
                babyProfile: profile,
                summaries: viewModel.babySummaries(for: profile)
            ) {
                onSelectItem(item)
            }
        }
    }

    @ViewBuilder
    private func skeletonCard(for item: ProductSelection) -> some View {
        switch item {
        case .myWeight:
            SnapshotSkeletonCardView(style: .weight)
        case .myBloodPressure:
            SnapshotSkeletonCardView(style: .bloodPressure)
        case .baby:
            SnapshotSkeletonCardView(style: .baby)
        }
    }

}
