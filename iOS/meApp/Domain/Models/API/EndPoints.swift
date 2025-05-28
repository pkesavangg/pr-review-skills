//
//  API.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//


import Foundation

enum API {
    static let baseURL = AppEnvironment.apiBaseURL
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
    case operationsR4(startTimestamp: String?)
    case submitOperation
    case operationsCSV(utcOffset: Int?, download: Bool?)
    case flags
    case clearFlag(flagId: String)
    case feed
    case markFeedAs(elementId: String)

    var urlRequest: URLRequest? {
        switch self {
        case .signup:
            return request(path: "/account/")
        case .login:
            return request(path: "/account/login/")
        case .logout:
            return request(path: "/account/logout")
        case .refreshToken:
            return request(path: "/refresh-token")
        case .accountInfo:
            return request(path: "/account/")
        case .updateAccount:
            return request(path: "/account/")
        case .changePassword:
            return request(path: "/account/password/")
        case .requestPasswordReset:
            return request(path: "/account/password-reset/request")
        case .checkPasswordResetToken:
            return request(path: "/account/password-reset/check")
        case .confirmPasswordReset:
            return request(path: "/account/password-reset/confirm")
        case .setGoal:
            return request(path: "/account/goal")
        case .updateProfile:
            return request(path: "/account/profile")
        case .updateBodyComp:
            return request(path: "/account/bodycomp")
        case .updateWeightless:
            return request(path: "/account/weightless")
        case .updateStreak:
            return request(path: "/account/streak")
        case .updateDashboardType:
            return request(path: "/account/dashboard-type")
        case .updateDashboardMetrics:
            return request(path: "/account/dashboard-metrics")
        case .updateNotifications:
            return request(path: "/account/notification")
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
            return request(path: "/operation")
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
            return request(path: "/account/flag")
        case .clearFlag(let flagId):
            return request(path: "/account/flag/\(flagId)")
        case .feed:
            return request(path: "/feed")
        case .markFeedAs(let elementId):
            guard let url = URL(string: "\(API.baseURL)/feed/\(elementId)") else { return nil }
            return URLRequest(url: url)
        case .deleteAccount:
            return request(path: "/account/")
        }
    }

    // MARK: - Shared URL Construction Helper
    private func request(path: String) -> URLRequest? {
        guard let url = URL(string: "\(API.baseURL)\(path)") else { return nil }
        return URLRequest(url: url)
    }
}
