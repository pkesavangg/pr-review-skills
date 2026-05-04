//
//  NotificationContainerView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//

import SwiftUI

/// NotificationContainerView serves as a centralized container for all application-wide notifications.
/// It acts as an overlay layer that manages different types of notifications:
/// - Alerts (via AlertModifier)
/// - Toasts (via ToastModifier)
/// - Loaders (via LoaderModifier)
/// - Modals (via ModalModifier)
///
/// Usage:
/// ```swift
/// // In SceneDelegate.swift
/// let appModalWindow = PassThroughWindow(windowScene: scene)
/// let appModalWindowController = UIHostingController(rootView: NotificationContainerView()
///     .themeable()
///     .environmentObject(appState.themeManager)
/// )
/// ```

struct NotificationContainerView: View {
    @StateObject var viewModel = NotificationContainerViewModel()
    @EnvironmentObject var themeManager: Theme
    var body: some View {
        VStack {}
        .presentAlert(alertData: $viewModel.alertData)
        .presentToast(data: $viewModel.toastData, dismissSignal: viewModel.dismissToastSignal)
        .presentLoader(loaderData: $viewModel.loaderData)
        .presentModal(modalStack: $viewModel.modalViewData)
        .preferredColorScheme(themeManager.getPreferredAppearanceMode())
    }
}
