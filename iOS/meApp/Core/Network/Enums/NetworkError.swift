//
//  NetworkError.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//


import Foundation

// MARK: - Network Error
enum NetworkError: Error, LocalizedError {
    case invalidURL
    case invalidResponse
    case decodingError
    case invalidRequest
    case statusCode(Int)
    case noInternet
    case timeout
    case unknown(Error)

    var errorDescription: String? {
        switch self {
        case .invalidURL: 
            return "Invalid URL"
        case .invalidResponse: 
            return "Invalid server response"
        case .decodingError: 
            return "Failed to decode response"
        case .invalidRequest: 
            return "Invalid request"
        case .statusCode(let code): 
            return "Server error with status code \(code)"
        case .noInternet: 
            return "No internet connection available"
        case .timeout: 
            return "Request timed out"
        case .unknown(let error): 
            return error.localizedDescription
        }
    }
}
