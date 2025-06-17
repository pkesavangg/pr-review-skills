//
//  TimePickerView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 17/06/25.
//

import SwiftUI

/// Reusable time picker that respects a maximum (latest) allowable time.
struct TimePickerView: View {
    /// Controls whether picker is visible.
    @Binding var isPresented: Bool
    /// Currently selected time.
    @Binding var time: Date
    /// Maximum selectable time (inclusive).
    var endTime: Date = Date()
    @Environment(\.appTheme) private var theme

    var body: some View {
        if isPresented {
            DatePicker(
                "",
                selection: $time,
                in: ...endTime,
                displayedComponents: .hourAndMinute
            )
            .datePickerStyle(.wheel)
            .labelsHidden()
            .tint(theme.actionPrimary)
            .padding(.bottom, .spacingXS)
            .background(
                RoundedRectangle(cornerRadius: .radiusSM)
                    .fill(theme.backgroundPrimary)
            )
            .padding(.top, .spacingXS)
            .frame(maxWidth: .infinity)
        }
    }
}

#Preview {
    TimePickerView(isPresented: .constant(true), time: .constant(Date()))
        .environmentObject(Theme.shared)
} 
