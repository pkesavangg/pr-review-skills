//
//  DatePickerView.swift
//  meApp
//
//  Created by Lakshmi Priya on 11/06/25.
//

import SwiftUI

/// A reusable date picker view with graphical style and theming support.
struct DatePickerView: View {
    /// Controls whether the date picker is shown.
    @Binding var isPresented: Bool
    /// The currently selected date.
    @Binding var date: Date
    /// The earliest selectable date (default: Jan 1, 1922).
    var startDate: Date = Date(timeIntervalSince1970: -1514764800) // Jan 1, 1922
    /// The latest selectable date (default: today).
    var endDate: Date = Date() // Current Date
    @Environment(\.appTheme) var theme

    var body: some View {
        if isPresented {
            DatePicker(
                "",
                selection: $date,
                in: startDate...endDate,
                displayedComponents: .date
            )
            .datePickerStyle(.graphical)
            .labelsHidden()
            .tint(theme.actionPrimary)
            .padding(.bottom, .spacingXS)
            .background(
                RoundedRectangle(cornerRadius: .radiusSM)
                    .fill(theme.backgroundPrimary)
            )
            .padding(.top, .spacingXS)
        }
    }
}

#Preview {
    DatePickerView(isPresented: .constant(true), date: .constant(Date()))
}


/*
 // Example usage of DatePickerView and DateLabelView in a parent view.

  struct ExampleUsage: View {
      @State private var showDatePicker = false // Controls date picker visibility
      @State private var selectedDate = Date() // Holds the selected date

      var body: some View {
          VStack {
              HStack {
                  Spacer()
                  // Tapping the date label toggles the date picker
                  DateLabelView(date: selectedDate) {
                      withAnimation { showDatePicker.toggle() }
                  }
                  Spacer()
                  Text("Time Picker")
                  Spacer()
              }
              // The date picker appears when showDatePicker is true
              DatePickerView(isPresented: $showDatePicker, date: $selectedDate)
          }
          .padding()
      }
  }

  struct ExampleUsage_Previews: PreviewProvider {
      static var previews: some View {
          ExampleUsage()
      }
  }


 */
