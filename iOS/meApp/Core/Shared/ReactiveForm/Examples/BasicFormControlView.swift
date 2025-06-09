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
    
    // Error message getters with priority
    var nameError: String? {
        if name.isDirty {
            if name.errors[.required] {
                return FormErrorMessages.required
            }
            
            if name.errors[.maxLength] {
                return FormErrorMessages.maxLength(10)
            }
        }
        return nil
    }
    
    var emailError: String? {
        if email.isDirty {
            if email.errors[.required] {
                return FormErrorMessages.required
            }
            if email.errors[.email] {
                return FormErrorMessages.email
            }
            if email.errors[.maxLength] {
                if let maxLength = email.errors.value(for: .maxLength) as? Int {
                    return FormErrorMessages.maxLength(maxLength)
                }
            }
        }
        return nil
    }
    
    var ageError: String? {
        if age.isDirty {
            if age.errors[.min] {
                // Get the minimum value from the validator
                if let minValue = age.errors.value(for: .min) as? Int {
                    return FormErrorMessages.min(minValue)
                }
            }
        }
        return nil
    }
    
    var rememberMeError: String? {
        if rememberMe.errors[.requiredTrue] {
            return FormErrorMessages.requiredTrue
        }
        return nil
    }
    
    var usernameError: String? {
        if username.isDirty {
            // Priority: required > no whitespace
            if username.errors[.required] {
                return FormErrorMessages.required
            }
            if username.errors[.noWhiteSpace] {
                return FormErrorMessages.noWhiteSpace
            }
        }
        return nil
    }
    
    var dobError: String? {
        if dob.errors[.futureDate] && dob.isDirty {
            return FormErrorMessages.futureDate
        }
        return nil
    }
    
    var passwordError: String? {
        if password.isDirty {
            if password.errors[.required] {
                return FormErrorMessages.required
            }
            if password.errors[.minLength] {
                if let minLength = password.errors.value(for: .minLength) as? Int {
                    return FormErrorMessages.minLength(minLength)
                }
            }
        }
        return nil
    }
    
    var confirmPasswordError: String? {
        if confirmPassword.isDirty {
            if confirmPassword.errors[.required] {
                return FormErrorMessages.required
            }
        }
        if formErrors[.passwordMatch] {
            return FormErrorMessages.passwordMatch
        }
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
    @State private var text: String = "false"
    
    var body: some View {
        Form {
            
            TextField("Name", text: $text)
            
            TextField("Name", text: $form.name.value)
            if let error = form.nameError {
                Text(error)
                    .foregroundColor(.red)
            }
            
            TextField("Email", text: $form.email.value)
            if let error = form.emailError {
                Text(error)
                    .foregroundColor(.red)
            }
            
            Stepper("Age: \(form.age.value)", value: $form.age.value, in: 0...100)
            if let error = form.ageError {
                Text(error)
                    .foregroundColor(.red)
            }
            
            Toggle("Remember Me", isOn: $form.rememberMe.value)
            if let error = form.rememberMeError {
                Text(error)
                    .foregroundColor(.red)
            }
            
            TextField("User Name", text: $form.username.value)
            if let error = form.usernameError {
                Text(error)
                    .foregroundColor(.red)
            }
            
            DatePicker("Date of Birth", selection: $form.dob.value, displayedComponents: .date)
            if let error = form.dobError {
                Text(error)
                    .foregroundColor(.red)
            }
            
            TextField("Password", text: $form.password.value)
            if let error = form.passwordError {
                Text(error)
                    .foregroundColor(.red)
            }
            
            TextField("Confirm Password", text: $form.confirmPassword.value)
            if let error = form.confirmPasswordError {
                Text(error)
                    .foregroundColor(.red)
            }
            
            Button("Submit") {
                if form.isValid {
                    print("Form submitted with:")
                    print("Name:", form.name.value)
                    print("Email:", form.email.value)
                }
            }
            .disabled(form.isInvalid)
            
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
