//
//  FeedLocalData.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 06/06/25.
//

/// Model to hold all feed-related data for an account
struct FeedLocalData: Codable {
    var settings: FeedSetting?
    var lastTriggeredTimestamp: String?
}
