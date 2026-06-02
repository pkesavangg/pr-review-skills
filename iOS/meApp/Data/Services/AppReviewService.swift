//
//  AppReviewService.swift
//  meApp
//
//  Created by AI Assistant on $(DATE).
//

import Combine
import Foundation
import StoreKit

/// Service responsible for handling app review prompts using StoreKit
@MainActor
final class AppReviewService: AppReviewHandlerProtocol, ReviewReportHandlerProtocol, ObservableObject {
    static let shared = AppReviewService()

    private let logger: LoggerServiceProtocol
    private let notificationHelper: NotificationHelperServiceProtocol
    private let reviewRepository: ScaleRepositoryAPIProtocol
    private let sleepHandler: @Sendable (UInt64) async -> Void
    private let hasActiveWindowScene: @MainActor () -> Bool
    private let nativeReviewRequest: @MainActor () -> Void
    private let tag = "AppReviewService"

    /// Delay before showing the review prompt (in nanoseconds)
    private let reviewPromptDelay: UInt64

    init(
        logger: LoggerServiceProtocol? = nil,
        notificationHelper: NotificationHelperServiceProtocol? = nil,
        reviewRepository: ScaleRepositoryAPIProtocol? = nil,
        reviewPromptDelay: UInt64 = UInt64(AppConstants.TimeoutsAndRetention.appReviewTriggerTimeout),
        sleepHandler: @escaping @Sendable (UInt64) async -> Void = { delayNanoseconds in
            try? await Task.sleep(nanoseconds: delayNanoseconds)
        },
        hasActiveWindowScene: @escaping @MainActor () -> Bool = {
            UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .contains { $0.activationState == .foregroundActive }
        },
        nativeReviewRequest: @escaping @MainActor () -> Void = {
            guard let windowScene = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .first(where: { $0.activationState == .foregroundActive }) else {
                return
            }

            if #available(iOS 18.0, *) {
                AppStore.requestReview(in: windowScene)
            } else {
                SKStoreReviewController.requestReview(in: windowScene)
            }
        }
    ) {
        self.logger = logger ?? LoggerService.shared
        self.notificationHelper = notificationHelper ?? NotificationHelperService.shared
        self.reviewRepository = reviewRepository ?? ScaleAPIRepository()
        self.reviewPromptDelay = reviewPromptDelay
        self.sleepHandler = sleepHandler
        self.hasActiveWindowScene = hasActiveWindowScene
        self.nativeReviewRequest = nativeReviewRequest
    }

    /// Triggers the native App Store review prompt using StoreKit
    /// - Parameter isFromDebug: Whether this is triggered from debug menu (affects timing)
    func triggerAppReview(isFromDebug: Bool = false) async {
        // Close any existing modals, alerts, or toasts before presenting the review modal
        if !isFromDebug {
            notificationHelper.dismissAllModals()
        }
        
        // Apply delay based on whether this is from debug or production flow
        let delayNanoseconds = isFromDebug ? UInt64(0) : reviewPromptDelay

        await sleepHandler(delayNanoseconds)

        // Request the review using StoreKit
        requestReview()
        logger.log(
            level: .info,
            tag: tag,
            message: "App review prompt triggered successfully"
        )
    }
    
    /// Submits a review report to the unified `POST /v3/review/` endpoint.
    /// Replaces the legacy `POST /v3/review/app` and `POST /v3/review/scale` endpoints.
    func submitReview(
        reviewType: ReviewType,
        status: ReviewStatus,
        rating: Int? = nil,
        sku: String? = nil,
        feedback: String? = nil,
        flagId: String? = nil
    ) async throws {
        let request = ReviewRequest(
            reviewType: reviewType,
            status: status,
            rating: rating,
            sku: sku,
            feedback: feedback,
            flagId: flagId
        )
        do {
            try await reviewRepository.submitReview(request)
            logger.log(
                level: .info,
                tag: tag,
                message: "Submitted \(reviewType.rawValue) review (status=\(status.rawValue)) to /v3/review/"
            )
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "Failed to submit review to /v3/review/: \(error.localizedDescription)"
            )
            throw error
        }
    }

    /// Requests the review using StoreKit's requestReview method
    @MainActor
    private func requestReview() {
        guard hasActiveWindowScene() else {
            logger.log(
                level: .info,
                tag: tag,
                message: "No active window scene found for review request"
            )
            return
        }

        nativeReviewRequest()
    }
}
