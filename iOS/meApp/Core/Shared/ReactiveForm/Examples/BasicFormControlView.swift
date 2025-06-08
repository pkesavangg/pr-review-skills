import SwiftUI
import Combine

class BasicProfileForm: ObservableForm {
    var name = FormControl("", validators: [.required])
    var email = FormControl("", validators: [.email, .minLength(5)])
    var age = FormControl(2, validators: [.min(2)])
    var rememberMe = FormControl(false, validators: [.requiredTrue])
    var username = FormControl("", validators: [.noWhiteSpace])
    var dob = FormControl(Date(), validators: [.futureDate])
    var password = FormControl("", validators: [.required, .minLength(6)])
    var confirmPassword = FormControl("", validators: [])
}

struct BasicFormControlView: View {
    @StateObject var form = BasicProfileForm()
    
    var body: some View {
        Form {
            TextField("Name", text: $form.name.value)

            if form.name.errors[.required] && form.name.isDirty {
                Text("Please fill a name.")
                    .foregroundColor(.red)
            }
            
            TextField("Email", text: $form.email.value)
            if form.email.errors[.email] {
                Text("Please fill a valid email.")
                    .foregroundColor(.red)
            }
            if form.email.errors[.minLength(5)] {
                Text("minimum 5 characters required.")
                    .foregroundColor(.red)
            }
            
            Stepper("Age: \(form.age.value)", value: $form.age.value, in: 0...100)
            
            
            if form.age.errors[.min(2)] {
                Text("Age must be at least 2.")
                    .foregroundColor(.red)
            }
            
            Toggle("Remember Me", isOn: $form.rememberMe.value)
        
            if form.rememberMe.errors[.requiredTrue] {
                Text("You must agree to remember me.")
                    .foregroundColor(.red)
            }
            
            TextField("User Name", text: $form.username.value)
            
            if form.username.errors[.noWhiteSpace] {
                Text("Username cannot be blank spaces.")
                    .foregroundColor(.red)
            }
            
            DatePicker("Date of Birth", selection: $form.dob.value, displayedComponents: .date)
            
            if form.dob.errors[.futureDate] {
                Text("Date of birth cannot be in the future.")
                    .foregroundColor(.red)
            }
            
            TextField("Password", text: $form.password.value)
            
            if form.password.errors[.required] {
                Text("Password is required.")
                    .foregroundColor(.red)
            }
            
            TextField("Confirm Password", text: $form.confirmPassword.value)
            if form.confirmPassword.errors[.matches(form.password.value)] {
                Text("Passwords do not match.")
                    .foregroundColor(.red)
            }
            
            Button("Submit") {
                //form.validate()
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
