//
//  ActivateYourScaleView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 02/07/25.
//

import SwiftUI

struct ActivateYourScaleView: View {
    private let lang = AppSyncStrings.ActivateYourScaleViewStrings.self
    var body: some View {
        ScaleInstructionView(title: lang.title, description: lang.description)
    }
}

#Preview {
    ActivateYourScaleView()
        .environmentObject(Theme.shared)
}
