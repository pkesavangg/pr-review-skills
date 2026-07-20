//  IntegrationsStrings.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 26/06/25.
//
import Foundation

/// Static strings used exclusively within the Integrations screen.
struct IntegrationsStrings {
    /// Navigation bar title.
    static let title = "Integrations"

    /// Integration provider titles.
    static let appleHealth = "Apple Health"
    static let fitbit = "Fitbit"
    static let myFitnessPal = "My Fitness Pal"

    /// Informational, non-interactive notice shown beneath the Fitbit row.
    /// Fitbit's legacy Web API is deprecated in Sept 2026; syncing moves to
    /// Google Health (coming soon). Display-only — see MOB-1608.
    static let fitbitDeprecationNotice = "Moving to Google Health in Sept 2026"

    /// Section headers grouping providers by the device types they support.
    static let weightScalesSectionTitle = "Integrations for weight scales"
    static let weightScalesAndBpmSectionTitle = "Integrations for Weight Devices & BPM"

    /// Request new integration button and modal.
    static let requestNewIntegration = "REQUEST NEW INTEGRATION"
    static let requestIntegrationTitle = "Request an Integration"
    static let requestIntegrationMessage = "What would you like to see added?"
    static let requestIntegrationPlaceholder = "integration"
    static let requestIntegrationSend = "SEND"
    static let requestIntegrationCancel = "CANCEL"

    static let requestIntegrationSuccessTitle = "Thanks for the suggestion"
    static let requestIntegrationSuccessMessage = "Our team will do our best to get it added in the future."
    static let requestIntegrationSuccessDismiss = "DISMISS"

    static let requestIntegrationErrorTitle = "Couldn't send request"
    static let requestIntegrationErrorMessage = "Something went wrong. Please try again."
    static let requestIntegrationErrorDismiss = "DISMISS"
}
