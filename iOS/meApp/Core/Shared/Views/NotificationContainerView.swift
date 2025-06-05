//
//  NotificationContainerView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//


import SwiftUI

struct NotificationContainerView: View {
    @StateObject var viewModel = NotificationContainerViewModel()
    @EnvironmentObject var themeManager: Theme
    var body: some View {
        VStack{}
        .presentAlert(alertData: $viewModel.alertData)
        .presentToast(data: $viewModel.toastData)
        .presentLoader(loaderData: $viewModel.loaderData)
        .presentModal(modalStack: $viewModel.modalViewData)
        .preferredColorScheme(themeManager.getPreferredAppearanceMode())
    }
}