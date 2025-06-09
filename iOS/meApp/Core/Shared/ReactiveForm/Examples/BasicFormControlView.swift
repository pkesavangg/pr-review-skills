import SwiftUI
import Combine

class BasicProfileForm: ObservableForm {
    var name = FormControl("", validators: [.required])
    var email = FormControl("", validators: [.required, .email, .maxLength(100)])
    var age = FormControl(5, validators: [.min(10)])
    var rememberMe = FormControl(false, validators: [.requiredTrue])
    var username = FormControl("", validators: [.noWhiteSpace])
    var dob = FormControl(Date(), validators: [.futureDate])
    var password = FormControl("", validators: [.required, .minLength(6)])
    var confirmPassword = FormControl("", validators: [])
    
    // Error message getters with priority
    var nameError: String? {
        if name.isDirty {
            if name.errors[.required] {
                return FormErrorMessages.required
            }
        }
        return nil
    }
    
    var emailError: String? {
        if email.isDirty {
            // Priority: required > email pattern > min length
            if email.errors[.required] {
                return FormErrorMessages.required
            }
            if email.errors[.email] {
                return FormErrorMessages.email
            }
            if email.errors[.minLength(100)] {
                return FormErrorMessages.minLength(100)
            }
        }
        return nil
    }
    
    var ageError: String? {
        if age.errors[.min(10)] {
            return FormErrorMessages.min(10)
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
            // Priority: required > min length
            if password.errors[.required] {
                return FormErrorMessages.required
            }
            if password.errors[.minLength(6)] {
                return FormErrorMessages.minLength(6)
            }
        }
        return nil
    }
    
    var confirmPasswordError: String? {
        return nil
    }
}

struct BasicFormControlView: View {
    @StateObject var form = BasicProfileForm()
    
    var body: some View {
        Form {
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
        }
        .navigationTitle("Basic Form Control")
    }
} 

#Preview {
    BasicFormControlView()
}
