//
//  PermissionsStore.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 08/07/25.
//

import Foundation
import Combine
import SwiftUI

@MainActor
final class PermissionsStore: ObservableObject {
    // MARK: - Published outputs
    /// `requiredCategories` that are *mandatory* – used for red status icons.
    @Published private(set) var requiredCategories: Set<PermissionListView.Category> = []

    // MARK: - Dependencies
    @Injector private var scaleService: ScaleService

    private var cancellables: Set<AnyCancellable> = []

    init() {
        // Derive the initial value from the current scales array
        updatePermissionSets(with: scaleService.scales)

        // Continue to observe for subsequent scale changes
        scaleService.$scales
            .receive(on: DispatchQueue.main)
            .sink { [weak self] scales in
                self?.updatePermissionSets(with: scales)
            }
            .store(in: &cancellables)
    }

    // MARK: - Internal helpers
    private func updatePermissionSets(with devices: [Device]) {
        // Reset
        var newRequired: Set<PermissionListView.Category> = []

        guard !devices.isEmpty else {
            requiredCategories = []
            return
        }

        for device in devices {
            let rawType = (device.bathScale?.scaleType ?? device.deviceType ?? "").lowercased()
            guard let scaleType = ScaleSourceType(rawValue: rawType) else { continue }

            switch scaleType {
            case .wifi, .espTouchWifi:
                newRequired.insert(.notifications)

            case .bluetooth, .lcbt, .lcbtScale, .bluetoothScale:
                newRequired.insert(.bluetooth)

            case .appsync, .appsyncScale:
                newRequired.insert(.camera)

            case .btWifiR4:
                newRequired.formUnion([.bluetooth, .notifications])
            }
        }

        requiredCategories = newRequired
    }
} 
