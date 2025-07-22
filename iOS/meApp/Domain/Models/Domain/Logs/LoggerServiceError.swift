//
//  LoggerServiceError.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 21/07/25.
//
import Foundation


/// Errors that can occur in LoggerService
enum LoggerServiceError: LocalizedError {
    case noActiveAccount
    case invalidData
    
    var errorDescription: String? {
        switch self {
        case .noActiveAccount:
            return "No active account found"
        case .invalidData:
            return "Invalid data format"
        }
    }
}
