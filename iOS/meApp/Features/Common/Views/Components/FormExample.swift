import SwiftUI

class ProfileForm: ObservableForm {
    @FormField(validators: [.required, .minLength(2)])
    var firstName: String = ""
    
    @FormField(validators: [.required, .email])
    var email: String = ""
    
    var age = FormControl(0, validators: [.min(18), .max(120)])
}

struct FormExample: View {
    @StateObject var form = ProfileForm()
    
    var body: some View {
        Form {
            Section(header: Text("Personal Information")) {
                TextField("First Name", text: $form.firstName)
                if form.$firstName.isDirty && form.$firstName.errors[.required] {
                    Text("Please enter your first name")
                        .foregroundColor(.red)
                } else if form.$firstName.errors[.minLength(2)] {
                    Text("Name must be at least 2 characters")
                        .foregroundColor(.red)
                }
                
                TextField("Email", text: $form.email)
                if form.$email.isDirty && form.$email.errors[.required] {
                    Text("Please enter your email")
                        .foregroundColor(.red)
                } else if form.$email.errors[.email] {
                    Text("Please enter a valid email")
                        .foregroundColor(.red)
                }
                
                Stepper("Age: \(form.age.value)", value: $form.age.value)
                if form.age.errors[.min(18)] {
                    Text("Must be at least 18 years old")
                        .foregroundColor(.red)
                } else if form.age.errors[.max(120)] {
                    Text("Age cannot exceed 120")
                        .foregroundColor(.red)
                }
            }
            
            Button("Submit") {
                form.validate()
                if form.isValid {
                    print("Form is valid!")
                    print("First Name:", form.firstName)
                    print("Email:", form.email)
                    print("Age:", form.age.value)
                }
            }
            .disabled(form.isInvalid)
        }
        .navigationTitle("Profile Form")
    }
} 