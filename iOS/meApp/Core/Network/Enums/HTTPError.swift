//
//  HTTPError.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//

import Foundation

// MARK: - HTTP Error
enum HTTPError: Error, LocalizedError, Equatable {
    case invalidURL
    case invalidResponse
    case decodingError
    case badRequest
    case forbidden
    case serverError
    case statusCode(Int)
    case apiError(message: String, code: Int)
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
        case .apiError(let message, _):
            return message
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

    static func == (lhs: HTTPError, rhs: HTTPError) -> Bool {
        switch (lhs, rhs) {
        case (.invalidURL, .invalidURL),
             (.invalidResponse, .invalidResponse),
             (.decodingError, .decodingError),
             (.badRequest, .badRequest),
             (.forbidden, .forbidden),
             (.serverError, .serverError),
             (.unauthorized, .unauthorized),
             (.notFound, .notFound),
             (.noInternet, .noInternet),
             (.timeout, .timeout):
            return true
        case (.statusCode(let lhsCode), .statusCode(let rhsCode)):
            return lhsCode == rhsCode
        case (.apiError(let lhsMessage, let lhsCode), .apiError(let rhsMessage, let rhsCode)):
            return lhsMessage == rhsMessage && lhsCode == rhsCode
        case (.unknown(let lhsError), .unknown(let rhsError)):
            return lhsError.localizedDescription == rhsError.localizedDescription
        default:
            return false
        }
    }

    static func isNetworkError(_ error: Error) -> Bool {
        if let networkError = error as? HTTPError {
            switch networkError {
            // Treat .timeout as a network error to avoid falsely marking accounts expired.
            case .noInternet, .timeout, .statusCode(0):
                return true
            default:
                return false
            }
        }
        // Fallback: treat any raw URLError as a network failure, not account expiry.
        if error is URLError {
            return true
        }
        return false
    }

    /// Returns true when `error` represents an HTTP 409 Conflict — used to detect a server-side
    /// "name already taken" rejection regardless of whether it arrives as `.statusCode` or `.apiError`.
    static func isConflict(_ error: Error) -> Bool {
        switch error as? HTTPError {
        case .statusCode(409): return true
        case .apiError(_, let code): return code == 409
        default: return false
        }
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
