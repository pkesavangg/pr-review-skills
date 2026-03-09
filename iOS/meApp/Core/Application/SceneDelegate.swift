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
    var window: UIWindow?
    var appModal: PassThroughWindow?
    private var appState = AppState()

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        if let windowScene = scene as? UIWindowScene {
            if AppRuntime.isRunningTests {
                let window = UIWindow(windowScene: windowScene)
                window.rootViewController = UIHostingController(rootView: EmptyView())
                self.window = window
                window.makeKeyAndVisible()
                return
            }
            setupMainWindow(in: windowScene)
            appModalWindow(in: windowScene)
        }
    }

    func setupMainWindow(in scene: UIWindowScene) {
        let window = UIWindow(windowScene: scene)
        let root = ContentView()
            .themeable()
            .weightUnitable()
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
            .environmentObject(appState.themeManager)
            .environmentObject(appState.accountService)

        let appModalWindowController = UIHostingController(rootView: modalRoot)
        appModalWindowController.view.backgroundColor = .clear
        appModalWindow.rootViewController = appModalWindowController
        appModalWindow.isHidden = false
        self.appModal = appModalWindow
    }
}
