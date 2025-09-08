//
//  AppIconView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 09/06/25.
//
import SwiftUI


// MARK: - AppIconView
/// AppIconView is a reusable SwiftUI view that displays an app icon.
struct AppIconView: View {
    var icon: String
    var size: IconSize = IconSize()

    var body: some View {
        Image(icon)
            .renderingMode(.template)
            .frame(width: size.width, height: size.height)
    }
}

#Preview(body: {
    AppIconView(icon: AppAssets.helpCircle)
})
