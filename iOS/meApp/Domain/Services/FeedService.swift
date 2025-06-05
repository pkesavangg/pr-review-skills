import Foundation

@MainActor
final class FeedService: FeedServiceProtocol, ObservableObject {
    static let shared = FeedService()
    
    private let apiRepo: FeedRepositoryAPIProtocol = FeedRepositoryAPI()
    private let localRepo = FeedRepositoryLocal()
    private let networkMonitor = NetworkMonitor.shared
    private let accountService = AccountService.shared
    
    @Published private(set) var feedItems: [FeedItem] = []
    @Published private(set) var feedSettings: FeedSetting?
    
    init() {
        Task {
            do {
               try await getFeedSettings()
            } catch {}            
        }
    }
    
    // MARK: - Feed Items Management
    
    func fetchFeedItems() async throws {
        guard networkMonitor.isConnected else {
            throw NetworkError.noInternet
        }
        
        do {
            let items = try await apiRepo.fetchFeedItems()
            self.feedItems = items
        } catch {
            if NetworkError.isNetworkError(error) {
                // Return existing items on network error
               throw NetworkError.noInternet
            }
            throw error
        }
    }
    
    func updateFeedItem(_ feedItem: FeedItem, actionType: FeedActionType, variationId: Int?) async throws {
        let action = buildFeedAction(actionType: actionType, variationId: variationId)
        
        do {
            try await apiRepo.updateFeedItem(feedPostId: feedItem.feedPostId, feedAction: action)
            
            // Update local state if item is marked as read
            if actionType == .read {
                await MainActor.run {
                    if let index = self.feedItems.firstIndex(where: { $0.elementId == feedItem.elementId }) {
                        var updatedItem = self.feedItems[index]
                        updatedItem.isUnread = false
                        self.feedItems[index] = updatedItem
                    }
                }
            }
        } catch {
            if NetworkError.isNetworkError(error) {
                // Handle offline case if needed
                throw NetworkError.noInternet
            }
            throw error
        }
    }
    
    func getUnreadFeedCount() -> Int {
        return feedItems.filter { $0.isUnread == true }.count
    }
    
    // MARK: - Feed Settings Management
    
    func initializeFeedSettings() async throws {
        if try await getFeedSettings() == nil {
            let defaultSettings = FeedSetting(showPopupMessage: true, showNotificationBadge: true)
            try await storeFeedSettings(defaultSettings)
        }
    }
    
    func storeFeedSettings(_ settings: FeedSetting) async throws {
        guard let accountId = accountService.activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        
        try await localRepo.storeFeedSettings(accountId: accountId, settings: settings)
        self.feedSettings = settings
    }
    
    @discardableResult
    func getFeedSettings() async throws -> FeedSetting? {
        guard let accountId = accountService.activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        
        let settings = try await localRepo.getFeedSettings(accountId: accountId)
        self.feedSettings = settings
        return settings
    }
    
    // MARK: - Feed Modal Management
    
    func checkAndShowFeedModal() async throws -> Bool {
        guard let settings = try await getFeedSettings(),
              settings.showPopupMessage else {
            return false
        }
        
        if let loginFeedItem = feedItems.first(where: { $0.trigger == .login }) {
            return try await showFeedModal(loginFeedItem)
        }
        
        return false
    }
    
    func getLastTriggeredTimestamp() async throws -> String? {
        guard let accountId = accountService.activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        
        return try await localRepo.getLastTriggeredTimestamp(accountId: accountId)
    }
    
    func setLastTriggeredTimestamp(_ timestamp: String) async throws {
        guard let accountId = accountService.activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        
        try await localRepo.setLastTriggeredTimestamp(accountId: accountId, timestamp: timestamp)
    }
    
    func showFeedModal(_ feedItem: FeedItem) async throws -> Bool {
        let currentTime = Date().timeIntervalSince1970 * 1000 // Convert to milliseconds
        
        if let lastTriggeredTime = try await getLastTriggeredTimestamp(),
           let lastTriggeredTimeDouble = Double(lastTriggeredTime) {
            let oneWeek: Double = 7 * 24 * 60 * 60 * 1000 // One week in milliseconds
            let cooldownTime = lastTriggeredTimeDouble + oneWeek
            
            if cooldownTime < currentTime {
                return try await handleFeedModal(feedItem, currentTime: currentTime)
            }
            return false
        } else {
            return try await handleFeedModal(feedItem, currentTime: currentTime)
        }
    }
    
    // MARK: - Cleanup
    
    func clearFeedData() async throws {
        guard let accountId = accountService.activeAccount?.accountId else {
            throw AccountError.noActiveAccount
        }
        
        try await localRepo.clearFeedData(accountId: accountId)
        self.feedItems = []
        self.feedSettings = nil
    }
    
    // MARK: - Private Helpers
    
    private func buildFeedAction(actionType: FeedActionType, variationId: Int?) -> FeedAction {
        let requiresMeta = !(actionType == .click || actionType == .read || actionType == .trigger)
        
        return FeedAction(
            action: actionType,
            osType: requiresMeta ? "ios" : nil,
            meta: requiresMeta ? FeedActionMeta(variationId: variationId) : nil
        )
    }
    
    private func handleFeedModal(_ feedItem: FeedItem, currentTime: Double) async throws -> Bool {
        // Set last triggered timestamp
        try await setLastTriggeredTimestamp(String(Int(currentTime)))
        
        // Mark as triggered
        try await updateFeedItem(feedItem, actionType: .trigger, variationId: nil)
        
        // Show modal using your preferred UI framework
        // This is where you'd implement the actual modal presentation
        // For example, using SwiftUI or UIKit
        
        return true
    }
} 
