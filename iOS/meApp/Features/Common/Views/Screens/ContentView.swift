//
//  ContentView.swift
//  meApp
//
//  Created by Barath Chittibabu on 27/05/25.
//

import SwiftUI

struct ContentView: View {
    @EnvironmentObject var themeManager: Theme
    @Environment(\.appTheme) private var theme
    @State var text: String = "dfsdfsdfs"
    @FocusState private var isFocused: Bool
    var body: some View {
        VStack {
            // Testing purpose it will replace by the actual content
            Text("Hello World!")
                .fontOpenSans(.heading1) // 60pt, Extra Bold
                .foregroundColor(theme.supportToastBackground)
                .onTapGesture {
                    themeManager.isDarkMode.toggle()
                }
            
            AppInputTestingField()
        }
        .preferredColorScheme(themeManager.getPreferredAppearanceMode())
    }
}

#Preview {
    ContentView()
        .environmentObject(Theme.shared)
}
