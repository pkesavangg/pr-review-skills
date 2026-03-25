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
    case updateDeviceInfo
    case operations(startTimestamp: String?)
    case operationsR4(startTimestamp: String?)
    case submitOperation
    case operationsCSV(utcOffset: Int?, download: Bool?)
    case operationsR4CSV(utcOffset: Int?, download: Bool?)
    case bpmOperations(startTimestamp: String?)
    case bpmOperationsCSV(utcOffset: Int?, download: Bool?)
    case flags
    case clearFlag(flagId: String)
    case feed
    case markFeedAs(elementId: String)
    case log
    case pairedScale
    case pairedScaleId(String)
    case pairedScaleInfo(String)
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
        case .operationsCSV(let utcOffset, let download):
                return csvRequest(path: "/operation/csv/", utcOffset: utcOffset, download: download)
        case .operationsR4CSV(let utcOffset, let download):
                return csvRequest(path: "/operation/r4/csv/", utcOffset: utcOffset, download: download)
        case .bpmOperations(let startTimestamp):
            var components = URLComponents(string: "\(API.baseURL)/operation/bpm")
            if let timestamp = startTimestamp {
                components?.queryItems = [URLQueryItem(name: "start", value: timestamp)]
            }
            guard let url = components?.url else { return nil }
            return URLRequest(url: url)
        case .bpmOperationsCSV(let utcOffset, let download):
            return csvRequest(path: "/operation/bpm/csv/", utcOffset: utcOffset, download: download)
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
