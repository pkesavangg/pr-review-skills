//
//  AppDelegate.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//


import Foundation
import SwiftUI

// MARK: - AppDelegate
/// The AppDelegate class is responsible for handling application-level events.
/// It works alongside SceneDelegate when using UIKit lifecycle within a SwiftUI app.
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        return true
    }
    
    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
        configuration.delegateClass = SceneDelegate.self
        return configuration
    }
}
