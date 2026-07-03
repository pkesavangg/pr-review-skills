//  IntegrationType.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 26/06/25.
//

import Foundation

/// Supported integration providers shown in Integrations settings.
/// Extend this enum when new integrations are added.
enum IntegrationItemType: CaseIterable {
    case appleHealth
    case fitbit
    case myFitnessPal

    /// snake_case provider key used for accessibility identifiers and logging.
    /// Must stay byte-identical to the Android `Modifier.testTag` suffix so a
    /// single Appium selector resolves on both platforms (SharedAccessibility rule 3).
    var snakeKey: String {
        switch self {
        case .appleHealth:  return "apple_health"
        case .fitbit:       return "fitbit"
        case .myFitnessPal: return "my_fitness_pal"
        }
    }

    /// Localized display name for UI.
    var displayName: String {
        switch self {
        case .appleHealth:  return IntegrationsStrings.appleHealth
        case .fitbit:       return IntegrationsStrings.fitbit
        case .myFitnessPal: return IntegrationsStrings.myFitnessPal
        }
    }

    /// Asset name of logo icon.
    var iconAsset: String {
        switch self {
        case .appleHealth:  return AppAssets.hkLogoSmall
        case .fitbit:       return AppAssets.fitbitLogoSmall
        case .myFitnessPal: return AppAssets.myFitnessLogoSmall
        }
    }
    
    func oauthURL(accountId: String) -> String? {
        switch self {
        case .fitbit:
            return AppConstants.OAuthURLs.fitbit(accountId: accountId)
        case .myFitnessPal:
            return AppConstants.OAuthURLs.mfPal(accountId: accountId)
        default:
            return nil
        }
    }
}
