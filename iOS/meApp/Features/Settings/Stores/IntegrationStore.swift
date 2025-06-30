//  IntegrationStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 26/06/25.
//

import Foundation
import SwiftUI

// MARK: - IntegrationStore
/// Observable store that manages the list of integrations shown in `IntegrationsScreen`.
/// Holds the selection state and exposes helper APIs to update it.
@MainActor
class IntegrationStore: ObservableObject {
    /// List of integrations to display.
    @Published var integrations: [IntegrationItem] = [
        .init(type: .appleHealth, isSelected: true),
        .init(type: .fitbit),
        .init(type: .myFitnessPal)
    ]

    /// Updates the currently selected integration ensuring only one item is selected at a time.
    /// - Parameter item: The integration item to select.
    func selectIntegration(item: IntegrationItem) {
        integrations = integrations.map { current in
            IntegrationItem(
                type: current.type,
                isSelected: current.type == item.type
            )
        }
    }
}