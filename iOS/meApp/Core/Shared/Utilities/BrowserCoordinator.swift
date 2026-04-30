//
//  Coordinator.swift
//  meApp
//
//  Created by Lakshmi Priya on 09/06/25.
//

import Foundation
import SafariServices

/// Coordinator to handle SFSafariViewControllerDelegate callbacks.
class BrowserCoordinator: NSObject, SFSafariViewControllerDelegate {
    let completion: (() -> Void)?
    
    init(completion: (() -> Void)?) {
        self.completion = completion
    }
    
    /// Called when the user dismisses the SafariViewController.
    func safariViewControllerDidFinish(_ controller: SFSafariViewController) {
        completion?()
    }
}
