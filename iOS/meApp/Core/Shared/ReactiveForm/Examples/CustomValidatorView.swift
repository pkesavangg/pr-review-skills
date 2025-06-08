import SwiftUI

extension Validator where Value == String {
    static let phoneNumber = Validator { value in
        let pattern = #"^\d{10}$"#
        return NSPredicate(format: "SELF MATCHES %@", pattern)
            .evaluate(with: value)
    }
    
    static func matchesPassword(_ other: String) -> Validator {
        Validator { value in
            value == other
        }
    }
}

class SignupForm: ObservableForm {
    var username = FormControl("", validators: [.required, .minLength(4)])
    var phone = FormControl("", validators: [.required, .phoneNumber])
    var password = FormControl("", validators: [.required, .minLength(8)])
    var confirmPassword = FormControl("", validators: [.required])
    
    func validatePasswordMatch() {
//        confirmPassword.validators = [.required, .matchesPassword(password.value)]
        confirmPassword.validate()
    }
}

struct CustomValidatorView: View {
    @StateObject var form = SignupForm()
    @State private var showingSuccess = false
    
    var body: some View {
        Form {
            Section(header: Text("Create Account")) {
                TextField("Username", text: $form.username.value)
                if form.username.isDirty {
                    if form.username.errors[.required] {
                        Text("Username is required")
                            .foregroundColor(.red)
                    } else if form.username.errors[.minLength(4)] {
                        Text("Username must be at least 4 characters")
                            .foregroundColor(.red)
                    }
                }
                
                TextField("Phone", text: $form.phone.value)
                if form.phone.isDirty {
                    if form.phone.errors[.required] {
                        Text("Phone number is required")
                            .foregroundColor(.red)
                    } else if form.phone.errors[.phoneNumber] {
                        Text("Please enter a valid 10-digit phone number")
                            .foregroundColor(.red)
                    }
                }
                
                SecureField("Password", text: $form.password.value)
                if form.password.isDirty {
                    if form.password.errors[.required] {
                        Text("Password is required")
                            .foregroundColor(.red)
                    } else if form.password.errors[.minLength(8)] {
                        Text("Password must be at least 8 characters")
                            .foregroundColor(.red)
                    }
                }
                
                SecureField("Confirm Password", text: $form.confirmPassword.value)

                if form.confirmPassword.isDirty {
                    if form.confirmPassword.errors[.required] {
                        Text("Please confirm your password")
                            .foregroundColor(.red)
                    } else if form.confirmPassword.errors[.matchesPassword(form.password.value)] {
                        Text("Passwords do not match")
                            .foregroundColor(.red)
                    }
                }
            }
            
            Button("Create Account") {
                form.validate()
                form.validatePasswordMatch()
                if form.isValid {
                    showingSuccess = true
                }
            }
            .disabled(form.isInvalid)
        }
        .navigationTitle("Sign Up")
        .alert("Success", isPresented: $showingSuccess) {
            Button("OK") { }
        } message: {
            Text("Account created successfully!")
        }
    }
} 

#Preview {
    CustomValidatorView()
}
