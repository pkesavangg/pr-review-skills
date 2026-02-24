//
//  EntrySource.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//

enum EntrySource: String, Codable, Equatable {
    case manual = "manual"
    case bluetooth = "bluetooth"
    case lcbtScale = "lcbt scale"
    case wifiScale = "wifi scale"
    case appsyncScale = "appsync scale"
}
