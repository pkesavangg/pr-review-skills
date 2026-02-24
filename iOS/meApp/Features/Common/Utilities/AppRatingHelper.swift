//
//  AppRatingHelper.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 24/06/25.
//

import StoreKit
import UIKit

struct AppRatingHelper {
    /// Requests the system app review prompt if available.
    static func requestReview() {
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }

        if #available(iOS 14.0, *) {
            SKStoreReviewController.requestReview(in: scene)
        }
    }
}
