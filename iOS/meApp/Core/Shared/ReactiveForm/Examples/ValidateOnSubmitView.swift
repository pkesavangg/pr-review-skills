import SwiftUI

class RegistrationForm: ObservableForm {
    var firstName = FormControl("", validators: [.required], type: .manually)
    var lastName = FormControl("", validators: [.required], type: .manually)
    var email = FormControl("", validators: [.email], type: .manually)
    var age = FormControl(0, validators: [.min(13)], type: .manually)
}

struct ValidateOnSubmitView: View {
    @StateObject var form = RegistrationForm()
    @State private var showingSuccess = false
    
    var body: some View {
        Form {
            Section(header: Text("Registration")) {
                TextField("First Name", text: $form.firstName.value)
                if form.firstName.errors[.required] {
                    Text("First name is required")
                        .foregroundColor(.red)
                }
                
                TextField("Last Name", text: $form.lastName.value)
                if form.lastName.errors[.required] {
                    Text("Last name is required")
                        .foregroundColor(.red)
                }
                
                TextField("Email", text: $form.email.value)
                if form.email.errors[.email] {
                    Text("Please enter a valid email")
                        .foregroundColor(.red)
                }
                
                Stepper("Age: \(form.age.value)", value: $form.age.value)
                if form.age.errors[.min(13)] {
                    Text("Must be at least 13 years old")
                        .foregroundColor(.red)
                }
            }
            
            Button("Register") {
                form.validate()
                if form.isValid {
                    showingSuccess = true
                }
            }
            .disabled(form.isInvalid)
        }
        .navigationTitle("Register")
        .alert("Success", isPresented: $showingSuccess) {
            Button("OK") { }
        } message: {
            Text("Registration successful!")
        }
    }
} 
