import Foundation
import Testing
@testable import meApp

/// Tests for the unified `/v3/review/` submission migrated into `AppReviewService` (MOB-383).
@Suite(.serialized)
@MainActor
struct AppReviewServiceReviewTests {

    private func makeSUT(logger: MockLoggerService = MockLoggerService()) -> (sut: AppReviewService, repo: MockScaleRepositoryAPI) {
        let repo = MockScaleRepositoryAPI()
        let sut = AppReviewService(
            logger: logger,
            notificationHelper: MockNotificationHelperService(),
            reviewRepository: repo
        )
        return (sut, repo)
    }

    @Test("submitReview: builds ReviewRequest and posts via repository")
    func submitReviewSuccess() async throws {
        let logger = MockLoggerService()
        let (sut, repo) = makeSUT(logger: logger)

        try await sut.submitReview(
            reviewType: .scale,
            status: .reviewed,
            rating: 5,
            sku: "0375",
            feedback: "Great product",
            flagId: "flag-1"
        )

        #expect(repo.submitReviewCalls == 1)
        let request = repo.lastSubmittedReview
        #expect(request?.reviewType == "scale")
        #expect(request?.status == "reviewed")
        #expect(request?.rating == 5)
        #expect(request?.sku == "0375")
        #expect(request?.feedback == "Great product")
        #expect(request?.flagId == "flag-1")
        #expect(logger.messages.contains { $0.contains("/v3/review/") })
    }

    @Test("submitReview: app exitA review needs no rating/sku")
    func submitReviewAppExit() async throws {
        let (sut, repo) = makeSUT()

        try await sut.submitReview(reviewType: .app, status: .exitA)

        #expect(repo.submitReviewCalls == 1)
        #expect(repo.lastSubmittedReview?.reviewType == "app")
        #expect(repo.lastSubmittedReview?.status == "exitA")
        #expect(repo.lastSubmittedReview?.rating == nil)
        #expect(repo.lastSubmittedReview?.sku == nil)
    }

    @Test("submitReview: monitor review forwards sku")
    func submitReviewMonitor() async throws {
        let (sut, repo) = makeSUT()

        try await sut.submitReview(reviewType: .monitor, status: .feedback, rating: 4, sku: "0602")

        #expect(repo.lastSubmittedReview?.reviewType == "monitor")
        #expect(repo.lastSubmittedReview?.sku == "0602")
    }

    @Test("submitReview: propagates and logs repository error")
    func submitReviewFailure() async throws {
        let logger = MockLoggerService()
        let (sut, repo) = makeSUT(logger: logger)
        repo.submitReviewError = HTTPError.serverError

        await #expect(throws: HTTPError.serverError) {
            try await sut.submitReview(reviewType: .app, status: .reviewed, rating: 5)
        }
        #expect(repo.submitReviewCalls == 1)
        #expect(logger.messages.contains { $0.contains("Failed to submit review") })
    }
}
