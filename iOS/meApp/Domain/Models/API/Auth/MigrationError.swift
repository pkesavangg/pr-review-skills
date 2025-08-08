//
//  MigrationError.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 08/08/25.
//
import Foundation

// MARK: - Migration Errors
enum MigrationError: LocalizedError {
    case noDataToMigrate
    case invalidDataFormat
    case conversionFailed
    case saveFailed
    case databaseConnectionFailed
    case queryPreparationFailed
    case dataConversionFailed
    
    var errorDescription: String? {
        switch self {
        case .databaseConnectionFailed:
            return "Failed to connect to SQLite database"
        case .queryPreparationFailed:
            return "Failed to prepare SQLite query"
        case .dataConversionFailed:
            return "Failed to convert SQLite data to SwiftData format"
        case .noDataToMigrate:
            return "No account data found to migrate from Ionic app"
        case .invalidDataFormat:
            return "Invalid data format in Ionic app storage"
        case .conversionFailed:
            return "Failed to convert Ionic account data to SwiftUI format"
        case .saveFailed:
            return "Failed to save migrated account data"
        }
    }
}
