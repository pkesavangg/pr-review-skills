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
    case confirmPasswordReset
    case setGoal
    case updateProfile
    case updateBodyComp
    case updateWeightless
    case updateStreak
    case updateDashboardType
    case updateDashboardMetrics
    case updateProgressMetrics
    case updateNotifications
    case updateMeasurementUnits
    case emailCheck
    case updateDeviceInfo
    case operations(startTimestamp: String?)
    case operationsR4(startTimestamp: String?)
    case submitOperation
    case submitEntries
    case entries(start: String?, cursor: String?, limit: Int?, category: String?, babyId: String?)
    case entriesCSV(category: String?, babyId: String?, download: Bool?, utcOffset: Int?, entryType: String?)
    case operationsCSV(utcOffset: Int?, download: Bool?)
    case operationsR4CSV(utcOffset: Int?, download: Bool?)
    case flags
    case clearFlag(flagId: String)
    case feed
    case markFeedAs(elementId: String)
    case log
    case pairedScale
    case pairedScaleId(String)
    case pairedScaleInfo(String)
    case pairedDevice(deviceType: String?)
    case pairedDeviceId(String)
    case baby
    case babyId(String)
    case review
    case scaleR4Preference
    case integrationProvider(String) 
    case integrationHealthDevice(String) 
    case integrationHealth
    case integrationHealthLog
    case wifiScale(request: String?)

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
        case .updateProgressMetrics:
            return request(path: "/account/progress-metrics")
        case .updateNotifications:
            return request(path: "/account/notification")
        case .updateMeasurementUnits:
            return request(path: "/account/measurement-units")
        case .emailCheck:
            return request(path: "/account/email-check")
        case .updateDeviceInfo:
            return request(path: "/account/device/")
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
        case .submitEntries:
            return request(path: "/entries/")
        case .entries(let start, let cursor, let limit, let category, let babyId):
            // GET /v3/entries/ — sync mode (?start=) or cursor pagination (?cursor=&limit=),
            // optionally scoped to a single product via ?category= and a single baby via
            // ?babyId=. Omitted params fall back to server defaults (limit 20, all categories).
            var components = URLComponents(string: "\(API.baseURL)/entries/")
            var queryItems: [URLQueryItem] = []
            if let start, !start.isEmpty {
                queryItems.append(URLQueryItem(name: "start", value: start))
            }
            if let cursor, !cursor.isEmpty {
                queryItems.append(URLQueryItem(name: "cursor", value: cursor))
            }
            if let limit {
                queryItems.append(URLQueryItem(name: "limit", value: "\(limit)"))
            }
            if let category, !category.isEmpty {
                queryItems.append(URLQueryItem(name: "category", value: category))
            }
            if let babyId, !babyId.isEmpty {
                queryItems.append(URLQueryItem(name: "babyId", value: babyId))
            }
            components?.queryItems = queryItems.isEmpty ? nil : queryItems
            guard let url = components?.url else { return nil }
            return URLRequest(url: url)
        case .entriesCSV(let category, let babyId, let download, let utcOffset, let entryType):
            // GET /v3/entries/csv — unified export. download="true" streams a file,
            // otherwise the server emails the report. utcOffset defaults to 0.
            var components = URLComponents(string: "\(API.baseURL)/entries/csv")
            var queryItems: [URLQueryItem] = []
            if let category, !category.isEmpty {
                queryItems.append(URLQueryItem(name: "category", value: category))
            }
            if let babyId, !babyId.isEmpty {
                queryItems.append(URLQueryItem(name: "babyId", value: babyId))
            }
            if let download, download {
                queryItems.append(URLQueryItem(name: "download", value: "true"))
            }
            queryItems.append(URLQueryItem(name: "utcOffset", value: "\(utcOffset ?? 0)"))
            if let entryType, !entryType.isEmpty {
                queryItems.append(URLQueryItem(name: "entryType", value: entryType))
            }
            components?.queryItems = queryItems
            guard let url = components?.url else { return nil }
            return URLRequest(url: url)
        case .operationsCSV(let utcOffset, let download):
                return csvRequest(path: "/operation/csv/", utcOffset: utcOffset, download: download)
        case .operationsR4CSV(let utcOffset, let download):
                return csvRequest(path: "/operation/r4/csv/", utcOffset: utcOffset, download: download)
        case .flags:
            return request(path: "/account/flag")
        case .clearFlag(let flagId):
            return request(path: "/account/flag/\(flagId)")
        case .feed:
            return request(path: "/feed/iam")
        case .markFeedAs(let elementId):
            guard let url = URL(string: "\(API.baseURL)/feed/iam/\(elementId)") else { return nil }
            return URLRequest(url: url)
        case .deleteAccount:
            return request(path: "/account/")
        case .log:
            return request(path: "/support/log")
        case .pairedScale:
            return request(path: "/paired-scale")
        case .pairedScaleId(let id):
            return request(path: "/paired-scale/\(id)")
        case .pairedScaleInfo(let id):
            return request(path: "/paired-scale/\(id)/info")
        case .pairedDevice(let deviceType):
            var components = URLComponents(string: "\(API.baseURL)/paired-device/")
            if let deviceType, !deviceType.isEmpty {
                components?.queryItems = [URLQueryItem(name: "deviceType", value: deviceType)]
            }
            guard let url = components?.url else { return nil }
            return URLRequest(url: url)
        case .pairedDeviceId(let id):
            return request(path: "/paired-device/\(id)")
        case .baby:
            return request(path: "/baby/")
        case .babyId(let id):
            return request(path: "/baby/\(id)")
        case .review:
            return request(path: "/review/")
        case .scaleR4Preference:
            return request(path: "/scale-r4/preference")
        case .integrationProvider(let provider):
            return request(path: "/integrations/\(provider)")
        case .integrationHealthDevice(let deviceId):
            return request(path: "/integrations/health/\(deviceId)")
        case .integrationHealth:
            return request(path: "/integrations/health")
        case .integrationHealthLog:
            return request(path: "/integrations/health/log")
        case .wifiScale(let request):
            var components = URLComponents(string: "\(API.baseURL)/account/scale")
            if let requestValue = request {
                components?.queryItems = [URLQueryItem(name: "r", value: requestValue)]
            }
            guard let url = components?.url else { return nil }
            return URLRequest(url: url)
        }
    }

    // MARK: - Shared URL Construction Helper
    private func request(path: String) -> URLRequest? {
        guard let url = URL(string: "\(API.baseURL)\(path)") else { return nil }
        return URLRequest(url: url)
    }
   
    // Helper for CSV-style endpoints with optional download and offset query parameters
    private func csvRequest(path: String, utcOffset: Int?, download: Bool?) -> URLRequest? {
        var components = URLComponents(string: "\(API.baseURL)\(path)")
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
    }
}
