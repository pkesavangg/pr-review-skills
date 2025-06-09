import SwiftUI
import Combine

// Add Profile Data Model
struct ProfileData {
    let name: String
    let email: String
    let age: Int
    let rememberMe: Bool
    let username: String
    let dob: Date
    let password: String
    let confirmPassword: String
}

class BasicProfileForm: ObservableForm {
    var name = FormControl("", validators: [.required, .maxLength(10)])
    var email = FormControl("", validators: [.required, .email, .maxLength(100)])
    var age = FormControl(5, validators: [.min(10)])
    var rememberMe = FormControl(false, validators: [.requiredTrue])
    var username = FormControl("", validators: [.noWhiteSpace])
    var dob = FormControl(Date(), validators: [.futureDate])
    var password = FormControl("", validators: [.required, .minLength(6)])
    var confirmPassword = FormControl("", validators: [.required])
    var website = FormControl("", validators: [.required, .url])

    override func validateForm() {
        var errors = ValidationErrors<Any>()
        
        // Check if passwords match when both are filled
        if !password.errors[.required] && !confirmPassword.errors[.required] {
            if password.value != confirmPassword.value {
                errors.update(
                    for: Validator<Any>(type: .passwordMatch) { _ in false },
                    value: false
                )
            }
        }
        
        updateFormErrors(errors)
    }
    
    // Function to update form values
    func updateFormValues(with profile: ProfileData) {
        name.value = profile.name
        email.value = profile.email
        age.value = profile.age
        rememberMe.value = profile.rememberMe
        username.value = profile.username
        dob.value = profile.dob
        password.value = profile.password
        confirmPassword.value = profile.confirmPassword
        
        // Validate all fields after update
        validate()
    }
    
    
    func getError<T>(for control: FormControl<T>) -> String? {
        guard control.isDirty else { return nil }
        
        if control.errors[.required] { return FormErrorMessages.required }
        if control.errors[.email] { return FormErrorMessages.email }
        if control.errors[.minLength], let minLength = control.errors.value(for: .minLength) as? Int {
            return FormErrorMessages.minLength(minLength)
        }
        if control.errors[.maxLength], let maxLength = control.errors.value(for: .maxLength) as? Int {
            return FormErrorMessages.maxLength(maxLength)
        }
        if control.errors[.min], let minValue = control.errors.value(for: .min) as? Int {
            return FormErrorMessages.min(minValue)
        }
        if control.errors[.noWhiteSpace] { return FormErrorMessages.noWhiteSpace }
        if control.errors[.futureDate] { return FormErrorMessages.futureDate }
        if control.errors[.requiredTrue] { return FormErrorMessages.requiredTrue }
        if control === confirmPassword && formErrors[.passwordMatch] { 
            return FormErrorMessages.passwordMatch 
        }
        if control.errors[.url] { return FormErrorMessages.url }
        
        return nil
    }
}

struct ExamplesTextField: View {
    @State private var text: String = "false"
    var body: some View {
        TextField("Name", text: $text)
    }
}

#Preview {
    ExamplesTextField()
}

struct BasicFormControlView: View {
    @StateObject var form = BasicProfileForm()
    
    var body: some View {
        Form {
            TextField("Name", text: $form.name.value)
            if let error = form.getError(for: form.name) {
                Text(error).foregroundColor(.red)
            }
            
            TextField("Url", text: $form.website.value)
            if let error = form.getError(for: form.website) {
                Text(error).foregroundColor(.red)
            }
            
            TextField("Email", text: $form.email.value)
            if let error = form.getError(for: form.email) {
                Text(error).foregroundColor(.red)
            }
            
            Stepper("Age: \(form.age.value)", value: $form.age.value, in: 0...100)
            if let error = form.getError(for: form.age) {
                Text(error).foregroundColor(.red)
            }
            
            Toggle("Remember Me", isOn: $form.rememberMe.value)
            if let error = form.getError(for: form.rememberMe) {
                Text(error).foregroundColor(.red)
            }
            
            TextField("User Name", text: $form.username.value)
            if let error = form.getError(for: form.username) {
                Text(error).foregroundColor(.red)
            }
            
            DatePicker("Date of Birth", selection: $form.dob.value, displayedComponents: .date)
            if let error = form.getError(for: form.dob) {
                Text(error).foregroundColor(.red)
            }
            
            TextField("Password", text: $form.password.value)
            if let error = form.getError(for: form.password) {
                Text(error).foregroundColor(.red)
            }
            
            TextField("Confirm Password", text: $form.confirmPassword.value)
            if let error = form.getError(for: form.confirmPassword) {
                Text(error).foregroundColor(.red)
            }
            
            Button("mark as pristine") {
                form.name.markAsPristine()
            }
            
            Button("Submit") {
                if form.isValid {
                    print("Form submitted with:")
                    print("Name:", form.name.value)
                    print("Email:", form.email.value)
                }
            }
            .disabled(!form.isValid)
            
            Button("Load Sample Data") {
                let sampleProfile = ProfileData(
                    name: "John Doe",
                    email: "john.doe@example.com",
                    age: 25,
                    rememberMe: true,
                    username: "johndoe",
                    dob: Calendar.current.date(byAdding: .year, value: -25, to: Date()) ?? Date(),
                    password: "password123",
                    confirmPassword: "password123"
                )
                form.updateFormValues(with: sampleProfile)
            }
        }
        .navigationTitle("Basic Form Control")
    }
}

#Preview {
    BasicFormControlView()
}
