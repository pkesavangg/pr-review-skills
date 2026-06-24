//
//  PermissionCategory.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 17/07/25.
//

// MARK: Enumerations
/// Distinct permission groups that can be rendered by the list.
enum PermissionCategory: CaseIterable, Hashable {
    case bluetooth, location, camera, internet, notifications
}
