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
    /// The selected calendar date for which the time should apply.
    /// We align the `time`'s day/month/year to this value to avoid cross-day clamping.
    var selectedDate: Date
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
                let aligned = clamp(align(time: time, to: selectedDate), to: endTime)
                if aligned != time {
                    time = aligned
                }
            }
            .onChange(of: endTime) { _, newEnd in
                let aligned = clamp(align(time: time, to: selectedDate), to: newEnd)
                if aligned != time {
                    time = aligned
                }
            }
            .onChange(of: selectedDate) { _, newDate in
                let aligned = clamp(align(time: time, to: newDate), to: endTime)
                if aligned != time {
                    time = aligned
                }
            }
        }
    }

    /// Aligns a Date's time-of-day to a specific calendar date (preserving hour/minute/second).
    private func align(time: Date, to date: Date) -> Date {
        let calendar = Calendar.current
        var dateComponents = calendar.dateComponents([.year, .month, .day], from: date)
        let timeComponents = calendar.dateComponents([.hour, .minute, .second], from: time)
        dateComponents.hour = timeComponents.hour
        dateComponents.minute = timeComponents.minute
        dateComponents.second = timeComponents.second
        return calendar.date(from: dateComponents) ?? time
    }

    /// Clamps a candidate time to be no later than the provided end time.
    private func clamp(_ candidate: Date, to end: Date) -> Date {
        candidate > end ? end : candidate
    }
}

#Preview {
    TimePickerView(isPresented: .constant(true),
                   time: .constant(Date()),
                   selectedDate: Date())
        .environmentObject(Theme.shared)
}
