//
//  NoteBox.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

struct NoteBox<Content: View>: View {
    let content: Content
    @Environment(\.appTheme) private var theme

    init(
        @ViewBuilder content: () -> Content
    ) {
        self.content = content()
    }

    var body: some View {
        HStack(alignment: .top, spacing: .spacingSM) {
            content
        }
        .padding(.spacingSM)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusSM)
    }
}

#Preview() {
    VStack(spacing: 12) {
        NoteBox {
            HStack {
                Text("Weight Only: On")
                    .fontOpenSans(.body2)
                    .foregroundColor(.black)
            }
        }
        NoteBox {
            VStack(alignment: .leading, spacing: 2) {
                Text("**A user has Weight Only Mode on** Only weight and BMI will be collected. You can temporarily enable All Body Metrics and/or review users from scale settings.")
                    .fontOpenSans(.body3)
                    .foregroundColor(.black)
            }
        }
        NoteBox {
            HStack {
                Text("Heart Rate: OFF")
                    .fontOpenSans(.body2)
                    .foregroundColor(.black)
                Spacer()                
            }
        }
        NoteBox {
            Text("**NOTE:** If you have certain medical conditions — like implanted medical devices or you are pregnant — you should not use All Body Metrics Mode without first consulting your doctor.")
                .fontOpenSans(.body3)
                .foregroundColor(.black)
        }
    }
    .padding(.spacingSM)
}
