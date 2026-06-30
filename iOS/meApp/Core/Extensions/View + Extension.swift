//
//  View + Extension.swift
//  meApp
//
//  Created by Lakshmi Priya on 09/06/25.
//

import SwiftUI

// MARK: inAppBrowser with FullScreen cover
public extension View {
    /// Presents an in-app browser with the given URL
    /// - Parameters:
    ///   - url: The URL to open
    ///   - isPresented: Binding to control the presentation
    ///   - presentationStyle: The presentation style for the browser
    ///   - completion: Optional completion handler called when the browser is dismissed
    func inAppBrowser(
        url: URL,
        isPresented: Binding<Bool>,
        presentationStyle: UIModalPresentationStyle = .automatic,
        completion: (() -> Void)? = nil
    ) -> some View {
        self.fullScreenCover(isPresented: isPresented) {
            SafariView(url: url, completion: completion)
        }
    }
}

// MARK: - Screen-level accessibility root
public extension View {
    /// Tags a screen's root container with a stable automation id **without the id bleeding
    /// onto the screen's child controls**.
    ///
    /// Applying `.accessibilityIdentifier(_:)` directly to the body container makes SwiftUI
    /// propagate that id down onto every descendant accessibility element that isn't its own
    /// clean leaf — e.g. the `NavbarHeaderView` Close/Help buttons — so their per-control ids
    /// resolve to the screen-root id instead of their own (MOB-1132).
    ///
    /// `.accessibilityElement(children: .contain)` first promotes the container into a single
    /// accessibility element that *holds* its children, so the id lands only on the container
    /// and each child keeps the id it set for itself.
    ///
    /// Use this for every `*ScreenRoot` identifier instead of a bare `.accessibilityIdentifier`.
    func screenAccessibilityRoot(_ identifier: String) -> some View {
        self
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier(identifier)
    }
}
