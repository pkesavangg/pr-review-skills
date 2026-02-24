//
//  URLHelper.swift
//  meApp
//
//  Created by Lakshmi Priya on 17/06/25.
//

import Foundation

struct URLHelper {
    private static let baseURL = URLStrings.baseUrl

    static func getURL(for endpoint: BrowserEndpoints, path: String = "legal") -> URL {
        let fullPath = baseURL + path + "/" + endpoint.rawValue
        return URL(string: fullPath) ?? getURL(for: .notFound, path: "")
    }
}
