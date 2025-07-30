class IAMScreenViewModel: ObservableObject {
    @Injector var feedService: FeedService
    
    func refreshFeed() {
        Task {
            await feedService.fetchFeedItems()
        }
    }
}