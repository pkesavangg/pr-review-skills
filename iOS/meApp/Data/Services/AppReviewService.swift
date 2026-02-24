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
final class AppReviewService: AppReviewHandlerProtocol, ObservableObject {
    static let shared = AppReviewService()
    
    @Injector private var logger: LoggerService
    @Injector private var notificationHelper: NotificationHelperService
    
    private let tag = "AppReviewService"
    
    /// Delay before showing the review prompt (in nanoseconds)
    private let reviewPromptDelay = AppConstants.TimeoutsAndRetention.appReviewTriggerTimeout
    
    private init() {}
    
    /// Triggers the native App Store review prompt using StoreKit
    /// - Parameter isFromDebug: Whether this is triggered from debug menu (affects timing)
    func triggerAppReview(isFromDebug: Bool = false) async {
        // Close any existing modals, alerts, or toasts before presenting the review modal
        if !isFromDebug {
            await notificationHelper.dismissAllModals()
        }
        
        // Apply delay based on whether this is from debug or production flow
        let delayNanoseconds = isFromDebug ? UInt64(0) : UInt64(reviewPromptDelay)
        
        try? await Task.sleep(nanoseconds: delayNanoseconds)
        
        do {
            // Request the review using StoreKit
            await requestReview()
            
            logger.log(
                level: .info,
                tag: tag,
                message: "App review prompt triggered successfully"
            )
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "Error occurred while triggering app review: \(error.localizedDescription)"
            )
        }
    }
    
    /// Requests the review using StoreKit's requestReview method
    @MainActor
    private func requestReview() async {
        // Ensure we're on the main thread for UI operations
        if let windowScene = await getActiveWindowScene() {
            AppStore.requestReview(in: windowScene)
        } else {
            // Fallback to the deprecated method if window scene is not available
            SKStoreReviewController.requestReview()
        }
    }
    
    /// Gets the active window scene for StoreKit review presentation
    @MainActor
    private func getActiveWindowScene() async -> UIWindowScene? {
        guard let windowScene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first(where: { $0.activationState == .foregroundActive }) else {
            
            logger.log(
                level: .info,
                tag: tag,
                message: "No active window scene found for review request"
            )
            return nil
        }
        
        return windowScene
    }
}
