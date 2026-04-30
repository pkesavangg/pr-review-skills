//
//  Environment.swift
//  meApp
//
//  Created by Barath Chittibabu on 27/05/25.
//

import Foundation

enum AppEnvironment: String {
    case dev = "DEV"
    case staging = "STAGING"
    case production = "PRODUCTION"

    static var current: AppEnvironment {
        let env = Bundle.main.infoDictionary?["APP_ENV"] as? String ?? "PRODUCTION"
        return AppEnvironment(rawValue: env) ?? .production
    }

    static var baseDomain: String {
        Bundle.main.infoDictionary?["API_BASE_URL"] as? String ?? ""
    }
    
    /// Returns the full base URL with the correct scheme (http/https) based on environment.
    static var apiBaseURL: String {
        let scheme: String
        switch current {
        case .production:
            scheme = "https://"
        case .dev, .staging:
            scheme = "http://"
        }
        return scheme + baseDomain
    }
}
