import Foundation
import Testing
import UIKit
@testable import meApp

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
        let service = AppReviewService(
            logger: logger,
            notificationHelper: notification,
            reviewPromptDelay: 0
        )

        await service.triggerAppReview()

        let hasActiveScene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .contains { $0.activationState == .foregroundActive }

        #expect(hasActiveScene == true)
        #expect(notification.dismissAllModalsCalls == 1)
        #expect(logger.messages.contains { $0.contains("App review prompt triggered successfully") })
    }
}
