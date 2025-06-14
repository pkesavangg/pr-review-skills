//
//  NetworkError.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//


import Foundation

// MARK: - Network Error
enum HTTPError: Error, LocalizedError {
    case invalidURL
    case invalidResponse
    case decodingError
    case badRequest
    case forbidden
    case serverError
    case statusCode(Int)
    case unauthorized
    case notFound
    case noInternet
    case timeout
    case unknown(Error)
    
    var errorDescription: String? {
        switch self {
        case .unauthorized:
            return "Unauthorized access"
        case .forbidden:
            return "Access forbidden"
        case .notFound:
            return "Not found"
        case .invalidURL:
            return "Invalid URL"
        case .invalidResponse:
            return "Invalid server response"
        case .decodingError:
            return "Failed to decode response"
        case .badRequest:
            return "Bad request"
        case .statusCode(let code):
            return "Unknown error occurred \(code)"
        case .noInternet:
            return "No internet connection available"
        case .timeout:
            return "Request timed out"
        case .unknown(let error):
            return error.localizedDescription
        case .serverError:
            return "Internal server error"
        }
    }
    
    static func isNetworkError(_ error: Error) -> Bool {
        if let networkError = error as? HTTPError {
            switch networkError {
            case .noInternet, .statusCode(0):
                return true
            default:
                return false
            }
        }
        return false
    }
    
    static func from(status: HTTPStatusCode) -> HTTPError {
        switch status {
        case .unauthorized: return .unauthorized
        case .forbidden: return .forbidden
        case .notFound: return .notFound
        case .networkError: return .noInternet
        case .badRequest: return .badRequest
        case .internalServerError: return .serverError
        default: return .statusCode(status.rawValue)
        }
    }
}
