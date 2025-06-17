//
//  ThemedImage.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 13/06/25.
//

import SwiftUI

// MARK: ThemedImage
/// A view that displays an image with a theme-based name
struct ThemedImage: View {
    let name: String
    var isSingleMode: Bool = false
    
    @EnvironmentObject var themeManager: Theme
    
    var body: some View {
        let imageName = isSingleMode
        ? name
        : (themeManager.isDarkMode ? "\(name)Dark" : name)
        
        Image(imageName)
            .renderingMode(.original) // optional: ensures colors aren't auto-tinted
            .resizable()
            .scaledToFit()
    }
}

#Preview {
    ThemedImage(name: AppAssets.stamp)
        .environmentObject(Theme.shared)
}
