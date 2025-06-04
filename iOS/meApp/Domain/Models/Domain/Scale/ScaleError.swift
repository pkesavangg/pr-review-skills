//
//  ScaleError.swift
//  meApp
//
//  Created by Lakshmi Priya on 04/06/25.
//

import Foundation

enum ScaleError: LocalizedError {
    case deviceNotFound(id: String)
    case apiSyncFailed(Error)
    case unknown(Error)
    
    var errorDescription: String? {
        switch self {
        case .deviceNotFound(let id):
            return "Device not found with ID: \(id)"
        case .apiSyncFailed(let error):
            return "Failed to sync with API: \(error.localizedDescription)"
        case .unknown(let error):
            return "Unknown error: \(error.localizedDescription)"
        }
    }
}
