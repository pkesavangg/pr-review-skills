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
    @Published private(set) var requiredCategories: Set<PermissionCategory> = []

    @Injector private var permissionService: PermissionsService

    private var cancellables: Set<AnyCancellable> = []

    // MARK: - Init
    init() {
        // Observe required permission changes from the service
        permissionService.$requiredCategories
            .receive(on: DispatchQueue.main)
            .sink { [weak self] categories in
                self?.requiredCategories = categories
            }
            .store(in: &cancellables)
    }
} 
