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
            .padding(.vertical, .spacingXS)
            .frame(maxWidth: .infinity)
            .background(
                RoundedRectangle(cornerRadius: .radiusMD)
                    .fill(theme.backgroundPrimary)
            )
            .onAppear {
                if time > endTime { time = endTime }
            }
            .onChange(of: endTime) { _, newEnd in
                if time > newEnd { time = newEnd }
            }
        }
    }
}

#Preview {
    TimePickerView(isPresented: .constant(true), time: .constant(Date()))
        .environmentObject(Theme.shared)
} 
