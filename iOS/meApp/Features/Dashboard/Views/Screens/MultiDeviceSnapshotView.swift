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
    /// The currently selected product — used to pick which baby to show (MOB-435).
    let selectedItem: ProductSelection
    @StateObject private var viewModel = MultiDeviceSnapshotViewModel()
    let onSelectItem: (ProductSelection) -> Void
    let onAddBaby: () -> Void

    var body: some View {
        // Collapse all baby items to just the last-active one (MOB-435).
        let snapshotItems = viewModel.snapshotItems(from: availableItems, selectedItem: selectedItem)

        VStack(spacing: .spacingSM) {
            ForEach(snapshotItems) { item in
                if viewModel.isSnapshotReady(item) || viewModel.hasLoadedSnapshots(for: snapshotItems) {
                    snapshotCard(for: item)
                } else {
                    skeletonCard(for: item)
                }
            }
        }
        .padding(.spacingSM)
        .task(id: snapshotTaskID) {
            // Only load data for items that will actually be shown (one baby max).
            await viewModel.loadSnapshots(availableItems: snapshotItems)
        }
    }

    /// Stable task ID — re-runs when the shown snapshot set changes, including
    /// when the user switches the active baby via the detail-dashboard dropdown.
    private var snapshotTaskID: Int {
        var hasher = Hasher()
        let snapshotItems = viewModel.snapshotItems(from: availableItems, selectedItem: selectedItem)
        for item in snapshotItems {
            hasher.combine(item)
        }
        return hasher.finalize()
    }

    // MARK: - Skeleton Placeholder

    private var snapshotSkeletonCards: some View {
        let snapshotItems = viewModel.snapshotItems(from: availableItems, selectedItem: selectedItem)
        return VStack(spacing: .spacingSM) {
            ForEach(Array(snapshotItems.prefix(3).enumerated()), id: \.offset) { _, item in
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
            if profile.isPendingSelection {
                NoBabySnapshotCard {
                    onAddBaby()
                }
            } else {
                BabySnapshotCard(
                    babyProfile: profile,
                    summaries: viewModel.babySummaries(for: profile)
                ) {
                    onSelectItem(item)
                }
                .accessibilityIdentifier(AccessibilityID.babyCard)
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
        case .baby(let profile):
            if profile.isPendingSelection {
                NoBabySnapshotCard { onAddBaby() }
            } else {
                SnapshotSkeletonCardView(style: .baby)
            }
        }
    }

}
