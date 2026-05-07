//
//  SceneDelegate.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//


import Foundation
import SwiftUI

// MARK: - SceneDelegate
/// Handles scene lifecycle and sets up multiple UIWindows,
/// including the main content window and the top-layer modal window.
class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    /// Upper bound for Dynamic Type. Smaller user choices flow through unchanged;
    /// anything larger (including the Accessibility sizes) is clamped to this value.
    /// On small devices (iPhone XS, iPhone 12 mini) the cap is lowered to `.large`
    /// (the unscaled default) so labels in tight layouts — e.g. the Dashboard's
    /// WEEK/MONTH/YEAR/TOTAL segmented control — don't truncate.
    private static var dynamicTypeCap: UIContentSizeCategory {
        (DevicePlatform.isMiniPhone || DevicePlatform.isSmallPhone)
            ? .large
            : .extraExtraLarge
    }

    var window: UIWindow?
    var appModal: PassThroughWindow?
    private var appState = AppState()
    private var contentSizeObserver: NSObjectProtocol?


    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        if let windowScene = scene as? UIWindowScene {
            setupMainWindow(in: windowScene)
            appModalWindow(in: windowScene)
            startObservingContentSizeCategory()
        }
    }

    deinit {
        if let observer = contentSizeObserver {
            NotificationCenter.default.removeObserver(observer)
        }
    }

    func setupMainWindow(in scene: UIWindowScene) {
        let window = UIWindow(windowScene: scene)
        let root = ContentView()
            .themeable()
            .weightUnitable()
            .buttonStyle(AppDefaultButtonStyle())
            .environmentObject(appState.themeManager)
            .environmentObject(appState.accountService)

        window.rootViewController = UIHostingController(rootView: root)
        self.window = window
        window.makeKeyAndVisible()
    }

    func appModalWindow(in scene: UIWindowScene) {
        let appModalWindow = PassThroughWindow(windowScene: scene)
        let modalRoot = NotificationContainerView()
            .themeable()
            .weightUnitable()
            .buttonStyle(AppDefaultButtonStyle())
            .environmentObject(appState.themeManager)
            .environmentObject(appState.accountService)

        let appModalWindowController = UIHostingController(rootView: modalRoot)
        appModalWindowController.view.backgroundColor = .clear
        appModalWindow.rootViewController = appModalWindowController
        appModalWindow.isHidden = false
        self.appModal = appModalWindow
    }

    private func startObservingContentSizeCategory() {
        applyDynamicTypeCap()
        contentSizeObserver = NotificationCenter.default.addObserver(
            forName: UIContentSizeCategory.didChangeNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.applyDynamicTypeCap()
        }
    }

    /// Clamps each window's `preferredContentSizeCategory` at `dynamicTypeCap`
    /// using the system value as the source of truth. Sizes below the cap pass
    /// through unchanged so user preferences still take effect.
    private func applyDynamicTypeCap() {
        let system = UIApplication.shared.preferredContentSizeCategory
        let capped = min(system, Self.dynamicTypeCap)
        window?.traitOverrides.preferredContentSizeCategory = capped
        appModal?.traitOverrides.preferredContentSizeCategory = capped
    }
}
