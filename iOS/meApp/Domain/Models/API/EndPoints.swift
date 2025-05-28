//
//  API.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//


import Foundation

enum API {
    static let baseURL = "https://api.weightgurus.com/v3"
}

enum Endpoint {
    case signup
    case login
    case logout
    case refreshToken
    case accountInfo
    case updateAccount
    case deleteAccount
    case changePassword
    case requestPasswordReset
    case checkPasswordResetToken
    case confirmPasswordReset
    case setGoal
    case updateProfile
    case updateBodyComp
    case updateWeightless
    case updateStreak
    case updateDashboardType
    case updateDashboardMetrics
    case updateNotifications
    case operations(startTimestamp: String?)
    case operationsR4(startTimestamp: String?) // New endpoint for /operation/r4
    case submitOperation
    case operationsCSV(utcOffset: Int?, download: Bool?)
    case flags
    case clearFlag(flagId: String)
    case feed
    case markFeedAs(action: String, elementId: String)
    
    var urlRequest: URLRequest? {
        switch self {
        case .signup:
            return postRequest(path: "/account/")
        case .login:
            return postRequest(path: "/account/login/")
        case .logout:
            return postRequest(path: "/account/logout")
        case .refreshToken:
            return postRequest(path: "/refresh-token")
        case .accountInfo:
            return getRequest(path: "/account/")
        case .updateAccount:
            return putRequest(path: "/account/")
        case .changePassword:
            return putRequest(path: "/account/password/")
        case .requestPasswordReset:
            return postRequest(path: "/account/password-reset/request")
        case .checkPasswordResetToken:
            return postRequest(path: "/account/password-reset/check")
        case .confirmPasswordReset:
            return postRequest(path: "/account/password-reset/confirm")
        case .setGoal:
            return postRequest(path: "/account/goal")
        case .updateProfile:
            return patchRequest(path: "/account/profile")
        case .updateBodyComp:
            return patchRequest(path: "/account/bodycomp")
        case .updateWeightless:
            return patchRequest(path: "/account/weightless")
        case .updateStreak:
            return patchRequest(path: "/account/streak")
        case .updateDashboardType:
            return patchRequest(path: "/account/dashboard-type")
        case .updateDashboardMetrics:
            return patchRequest(path: "/account/dashboard-metrics")
        case .updateNotifications:
            return patchRequest(path: "/account/notification")
        case .operations(let startTimestamp):
            var components = URLComponents(string: "\(API.baseURL)/operation")
            if let timestamp = startTimestamp {
                components?.queryItems = [URLQueryItem(name: "start", value: timestamp)]
            }
            guard let url = components?.url else { return nil }
            return URLRequest(url: url)
        case .operationsR4(let startTimestamp):
            var components = URLComponents(string: "\(API.baseURL)/operation/r4")
            if let timestamp = startTimestamp {
                components?.queryItems = [URLQueryItem(name: "start", value: timestamp)]
            }
            guard let url = components?.url else { return nil }
            return URLRequest(url: url)
        case .submitOperation:
            return postRequest(path: "/operation")
        case .operationsCSV(let utcOffset, let download):
            var components = URLComponents(string: "\(API.baseURL)/operation/csv")
            var queryItems: [URLQueryItem] = []
            if let offset = utcOffset {
                queryItems.append(URLQueryItem(name: "utcOffset", value: "\(offset)"))
            }
            if let shouldDownload = download, shouldDownload {
                queryItems.append(URLQueryItem(name: "download", value: "true"))
            }
            components?.queryItems = queryItems
            guard let url = components?.url else { return nil }
            return URLRequest(url: url)
        case .flags:
            return getRequest(path: "/account/flag")
        case .clearFlag(let flagId):
            return deleteRequest(path: "/account/flag/\(flagId)")
        case .feed:
            return getRequest(path: "/feed")
        case .markFeedAs(let action, let elementId):
            guard let url = URL(string: "\(API.baseURL)/feed/\(elementId)") else { return nil }
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            let body = ["action": action]
            request.httpBody = try? JSONSerialization.data(withJSONObject: body)
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            return request
        case .deleteAccount:
            return deleteRequest(path: "/account/")
        }
    }

    // MARK: - Helper Methods

    private func getRequest(path: String) -> URLRequest? {
        guard let url = URL(string: "\(API.baseURL)\(path)") else { return nil }
        return URLRequest(url: url)
    }

    private func postRequest(path: String) -> URLRequest? {
        guard let url = URL(string: "\(API.baseURL)\(path)") else { return nil }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        return request
    }

    private func putRequest(path: String) -> URLRequest? {
        guard let url = URL(string: "\(API.baseURL)\(path)") else { return nil }
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        return request
    }

    private func patchRequest(path: String) -> URLRequest? {
        guard let url = URL(string: "\(API.baseURL)\(path)") else { return nil }
        var request = URLRequest(url: url)
        request.httpMethod = "PATCH"
        return request
    }

    private func deleteRequest(path: String) -> URLRequest? {
        guard let url = URL(string: "\(API.baseURL)\(path)") else { return nil }
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        return request
    }
}