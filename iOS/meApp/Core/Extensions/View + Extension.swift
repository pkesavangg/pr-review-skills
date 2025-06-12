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
    func inAppBrowser(url: URL,
                     isPresented: Binding<Bool>,
                     presentationStyle: UIModalPresentationStyle = .automatic,
                     completion: (() -> Void)? = nil) -> some View {
        self.fullScreenCover(isPresented: isPresented) {
            SafariView(url: url, completion: completion)
        }
    }
}
