//
//  AppIconView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 09/06/25.
//
import SwiftUI

struct AppIconView: View {
    var icon: String
    var size: CGFloat = 20  // Default size

    var body: some View {
        Image(icon)
            .renderingMode(.template)
            .resizable()
            .frame(width: size, height: size)
    }
}

#Preview(body: {
    AppIconView(icon: AppAssets.helpCircle, size: 40)
})
