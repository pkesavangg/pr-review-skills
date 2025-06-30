//  HealthKitIntegrationListItemView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 27/06/25.

import SwiftUI

// MARK: - HealthKitIntegrationListItemView
/// A dedicated list row for managing the Apple Health integration.
/// Internally holds its own `HealthKitStore` to reflect and mutate the
/// current on/off status so that parent views don't have to deal with it.
struct HealthKitIntegrationListItemView: View {
    @StateObject private var hkStore = HealthKitStore()

    var body: some View {
        IntegrationListItemView(
            item: IntegrationItem(type: .appleHealth, isSelected: hkStore.isIntegrated)
        ) {
            hkStore.handleRowTap()
        }
        // Present the Health-Access flow
        .sheet(item: $hkStore.activeState) { state in
            HKIntegrationHealthAccessView(
                state: state,
                onDismiss: { hkStore.dismissModal() },
                primaryAction: { hkStore.handlePrimaryAction(for: state) }
            )
        }
    }
}

// MARK: - Preview
#Preview {
    List {
        Section {
            HealthKitIntegrationListItemView()
                .listRowInsets()
        }
    }
    .listStyle(.insetGrouped)
    .scrollContentBackground(.hidden)
    .environmentObject(Theme.shared)
} 
