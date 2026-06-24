//
//  IAMScreenViewModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 30/07/25.
//
import Foundation

class IAMScreenViewModel: ObservableObject {
    @Injector var feedService: FeedServiceProtocol
    
    func refreshFeed() {
        Task {
            await feedService.fetchFeedItems()
        }
    }
}
