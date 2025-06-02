//
//  HTTPStatusCode.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//


enum HTTPStatusCode: Int {
    case networkError = 0
    case ok = 200
    case created = 201
    case accepted = 202
    case noContent = 204

    case badRequest = 400
    case unauthorized = 401
    case forbidden = 403
    case notFound = 404

    case conflict = 409
    case tooManyRequests = 429

    case internalServerError = 500
    case badGateway = 502
    case serviceUnavailable = 503

    var isSuccess: Bool {
        return (200...299).contains(self.rawValue)
    }
}

