//import SwiftUI
//
//class SettingsForm: ObservableForm {
//    @FormField(validators: [.required])
//    var username: String = ""
//    
//    @FormField(validators: [.email])
//    var email: String = ""
//    
//    @FormField(validators: [.minLength(8)])
//    var password: String = ""
//}
//
//struct FormFieldWrapperView: View {
//    @StateObject var form = SettingsForm()
//    
//    var body: some View {
//        Form {
//            Section(header: Text("Account Settings")) {
//                TextField("Username", text: $form.username)
//                if form.$username.errors[.required] {
//                    Text("Username is required")
//                        .foregroundColor(.red)
//                }
//                
//                TextField("Email", text: $form.email)
//                if form.$email.errors[.email] {
//                    Text("Please enter a valid email")
//                        .foregroundColor(.red)
//                }
//                
//                SecureField("Password", text: $form.password)
//                if form.$password.errors[.minLength(8)] {
//                    Text("Password must be at least 8 characters")
//                        .foregroundColor(.red)
//                }
//            }
//            
//            Button("Save Settings") {
//                form.validate()
//                if form.isValid {
//                    print("Settings saved!")
//                    print("Username:", form.username)
//                    print("Email:", form.email)
//                    print("Password:", "****")
//                }
//            }
//            .disabled(form.isInvalid)
//        }
//        .navigationTitle("Settings Form")
//    }
//} 
