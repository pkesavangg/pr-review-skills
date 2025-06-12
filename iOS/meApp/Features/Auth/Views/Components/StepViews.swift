import SwiftUI


struct EmailStepView: View {
    @Binding var email: String
    
    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            Text("What's your email?")
                .font(.title)
                .fontWeight(.bold)
            
            Text("We'll send you a confirmation link.")
                .foregroundColor(.secondary)
            
            TextField("email@example.com", text: $email)
                .textFieldStyle(.roundedBorder)
                .textContentType(.emailAddress)
                .keyboardType(.emailAddress)
                .autocapitalization(.none)
        }
    }
}

struct PasswordStepView: View {
    @Binding var password: String
    @State private var isSecure = true
    
    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            Text("Create a password")
                .font(.title)
                .fontWeight(.bold)
            
            Text("Use at least 8 characters.")
                .foregroundColor(.secondary)
            
            HStack {
                if isSecure {
                    SecureField("password", text: $password)
                        .textContentType(.newPassword)
                } else {
                    TextField("password", text: $password)
                        .textContentType(.newPassword)
                }
                
                Button(action: { isSecure.toggle() }) {
                    Image(systemName: isSecure ? "eye.slash" : "eye")
                        .foregroundColor(.secondary)
                }
            }
            .textFieldStyle(.roundedBorder)
        }
    }
} 
