//
//  AppInfo.swift
//  meApp
//
//  Created by Copilot on 17/06/25.
//

import Foundation

struct AppInfo {
    static let versionKey = "CFBundleShortVersionString"
    static var appVersion: String {
        Bundle.main.infoDictionary?[versionKey] as? String ?? "Unknown"
    }
}
