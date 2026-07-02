import Foundation
@testable import meApp
import Testing

private actor DelayRecorder {
    private var values: [UInt64] = []

    func append(_ value: UInt64) {
        values.append(value)
    }

    func snapshot() -> [UInt64] {
        values
    }
}

@Suite(.serialized)
@MainActor
struct AppReviewServiceTests {
    @Test("non-debug eligible path dismisses modals, waits configured delay, and requests review")
    func nonDebugEligiblePathRequestsReview() async {
        let notification = MockNotificationHelperService()
        let logger = MockLoggerService()
        let delayRecorder = DelayRecorder()
        var requestReviewCalls = 0

        let service = AppReviewService(
            logger: logger,
            notificationHelper: notification,
            reviewPromptDelay: 2_000,
            sleepHandler: { delay in
                await delayRecorder.append(delay)
            },
            hasActiveWindowScene: { true },
            nativeReviewRequest: {
                requestReviewCalls += 1
            }
        )

        await service.triggerAppReview()

        #expect(notification.dismissAllModalsCalls == 1)
        #expect(await delayRecorder.snapshot() == [2_000])
        #expect(requestReviewCalls == 1)
        #expect(logger.messages.contains { $0.contains("App review prompt triggered successfully") })
    }

    @Test("debug trigger skips modal dismissal and uses zero delay")
    func debugTriggerSkipsDismissAndDelay() async {
        let notification = MockNotificationHelperService()
        let logger = MockLoggerService()
        let delayRecorder = DelayRecorder()
        var requestReviewCalls = 0

        let service = AppReviewService(
            logger: logger,
            notificationHelper: notification,
            reviewPromptDelay: 2_000,
            sleepHandler: { delay in
                await delayRecorder.append(delay)
            },
            hasActiveWindowScene: { true },
            nativeReviewRequest: {
                requestReviewCalls += 1
            }
        )

        await service.triggerAppReview(isFromDebug: true)

        #expect(notification.dismissAllModalsCalls == 0)
        #expect(await delayRecorder.snapshot() == [0])
        #expect(requestReviewCalls == 1)
        #expect(logger.messages.contains { $0.contains("App review prompt triggered successfully") })
    }

    @Test("ineligible non-debug path suppresses review request and logs no-active-scene")
    func ineligibleNonDebugPathSuppressesReview() async {
        let notification = MockNotificationHelperService()
        let logger = MockLoggerService()
        let delayRecorder = DelayRecorder()
        var requestReviewCalls = 0

        let service = AppReviewService(
            logger: logger,
            notificationHelper: notification,
            reviewPromptDelay: 1_000,
            sleepHandler: { delay in
                await delayRecorder.append(delay)
            },
            hasActiveWindowScene: { false },
            nativeReviewRequest: {
                requestReviewCalls += 1
            }
        )

        await service.triggerAppReview()

        #expect(notification.dismissAllModalsCalls == 1)
        #expect(await delayRecorder.snapshot() == [1_000])
        #expect(requestReviewCalls == 0)
        #expect(logger.messages.contains { $0.contains("No active window scene found for review request") })
    }

    @Test("ineligible debug path suppresses review without dismissing modals")
    func ineligibleDebugPathSuppressesReviewWithoutDismiss() async {
        let notification = MockNotificationHelperService()
        let logger = MockLoggerService()
        let delayRecorder = DelayRecorder()
        var requestReviewCalls = 0

        let service = AppReviewService(
            logger: logger,
            notificationHelper: notification,
            reviewPromptDelay: 1_000,
            sleepHandler: { delay in
                await delayRecorder.append(delay)
            },
            hasActiveWindowScene: { false },
            nativeReviewRequest: {
                requestReviewCalls += 1
            }
        )

        await service.triggerAppReview(isFromDebug: true)

        #expect(notification.dismissAllModalsCalls == 0)
        #expect(await delayRecorder.snapshot() == [0])
        #expect(requestReviewCalls == 0)
        #expect(logger.messages.contains { $0.contains("No active window scene found for review request") })
    }

    @Test("frequency: every eligible trigger requests review exactly once")
    func frequencyEligiblePathRequestsOncePerTrigger() async {
        let notification = MockNotificationHelperService()
        let logger = MockLoggerService()
        let delayRecorder = DelayRecorder()
        var requestReviewCalls = 0

        let service = AppReviewService(
            logger: logger,
            notificationHelper: notification,
            reviewPromptDelay: 777,
            sleepHandler: { delay in
                await delayRecorder.append(delay)
            },
            hasActiveWindowScene: { true },
            nativeReviewRequest: {
                requestReviewCalls += 1
            }
        )

        await service.triggerAppReview()
        await service.triggerAppReview()
        await service.triggerAppReview(isFromDebug: true)

        #expect(notification.dismissAllModalsCalls == 2)
        #expect(await delayRecorder.snapshot() == [777, 777, 0])
        #expect(requestReviewCalls == 3)
    }

    @Test("eligibility transition: suppressed call does not block later eligible call")
    func eligibilityTransitionFromSuppressedToEligible() async {
        let notification = MockNotificationHelperService()
        let logger = MockLoggerService()
        let delayRecorder = DelayRecorder()
        var requestReviewCalls = 0
        var checks = 0

        let service = AppReviewService(
            logger: logger,
            notificationHelper: notification,
            reviewPromptDelay: 42,
            sleepHandler: { delay in
                await delayRecorder.append(delay)
            },
            hasActiveWindowScene: {
                checks += 1
                return checks > 1
            },
            nativeReviewRequest: {
                requestReviewCalls += 1
            }
        )

        await service.triggerAppReview()
        await service.triggerAppReview()

        #expect(notification.dismissAllModalsCalls == 2)
        #expect(await delayRecorder.snapshot() == [42, 42])
        #expect(requestReviewCalls == 1)
        #expect(logger.messages.contains { $0.contains("No active window scene found for review request") })
    }

    @Test("default handlers execute against host scene without crashing")
    func defaultHandlersExecuteOnHostScene() async {
        let notification = MockNotificationHelperService()
        let logger = MockLoggerService()
        var requestReviewCalls = 0

        let service = AppReviewService(
            logger: logger,
            notificationHelper: notification,
            reviewPromptDelay: 0,
            hasActiveWindowScene: { true },
            nativeReviewRequest: {
                requestReviewCalls += 1
            }
        )

        await service.triggerAppReview()

        #expect(notification.dismissAllModalsCalls == 1)
        #expect(requestReviewCalls == 1)
        #expect(logger.messages.contains { $0.contains("App review prompt triggered successfully") })
    }

    // MARK: - submitReview (unified /v3/review/ endpoint)

    @Test("submitReview success: sends correct request fields to repository and logs success")
    func submitReviewSuccess() async throws {
        let logger = MockLoggerService()
        let repo = MockScaleRepositoryAPI()
        let service = AppReviewService(
            logger: logger,
            notificationHelper: MockNotificationHelperService(),
            reviewRepository: repo,
            hasActiveWindowScene: { true },
            nativeReviewRequest: {}
        )

        try await service.submitReview(reviewType: .app, status: .ios, rating: 5, flagId: "flag-1")

        #expect(repo.submitReviewCalls == 1)
        #expect(repo.lastSubmittedReview?.reviewType == ReviewType.app.rawValue)
        #expect(repo.lastSubmittedReview?.status == ReviewStatus.ios.rawValue)
        #expect(repo.lastSubmittedReview?.rating == 5)
        #expect(repo.lastSubmittedReview?.flagId == "flag-1")
        #expect(logger.messages.contains { $0.contains("/v3/review/") })
    }

    @Test("submitReview failure: rethrows repository error and logs structured fields")
    func submitReviewFailure() async throws {
        let logger = MockLoggerService()
        let repo = MockScaleRepositoryAPI()
        repo.submitReviewError = NSError(domain: "test", code: 500)
        let service = AppReviewService(
            logger: logger,
            notificationHelper: MockNotificationHelperService(),
            reviewRepository: repo,
            hasActiveWindowScene: { true },
            nativeReviewRequest: {}
        )

        await #expect(throws: (any Error).self) {
            try await service.submitReview(reviewType: .scale, status: .exitA, sku: "0375")
        }
        #expect(repo.submitReviewCalls == 1)
        #expect(logger.messages.contains { $0.contains("Failed to submit review") })
    }

    @Test("submitReview scale type: passes sku field in request")
    func submitReviewScaleTypePassesSku() async throws {
        let repo = MockScaleRepositoryAPI()
        let service = AppReviewService(
            logger: MockLoggerService(),
            notificationHelper: MockNotificationHelperService(),
            reviewRepository: repo,
            hasActiveWindowScene: { true },
            nativeReviewRequest: {}
        )

        try await service.submitReview(reviewType: .scale, status: .reviewed, rating: 4, sku: "0375")

        #expect(repo.lastSubmittedReview?.sku == "0375")
        #expect(repo.lastSubmittedReview?.reviewType == ReviewType.scale.rawValue)
    }
}
