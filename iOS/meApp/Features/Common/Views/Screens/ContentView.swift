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
    var body: some View {
        VStack {
            // Testing purpose it will replace by the actual content
            BasicFormControlView()
                
        }
        .preferredColorScheme(themeManager.getPreferredAppearanceMode())
    }
}

#Preview {
    ContentView()
        .environmentObject(Theme.shared)
}
