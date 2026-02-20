//
//  OperationType.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//

/// /// Represents the type of operation that was performed on the entry.
enum OperationType: String, Codable, Equatable {
    case create = "create"
    case update = "update"
    case delete = "delete"
}
