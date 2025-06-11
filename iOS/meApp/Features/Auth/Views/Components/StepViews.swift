import SwiftUI

//TODO: These are for the testing purpose need to replace with the actual views

struct DateOfBirthStepView: View {
    @Binding var date: Date
    
    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            Text("When were you born?")
                .font(.title)
                .fontWeight(.bold)
            
            Text("This helps us personalize your experience.")
                .foregroundColor(.secondary)
            
            DatePicker(
                "Date of Birth",
                selection: Binding(
                    get: { date },
                    set: { date = $0 }
                ),
                displayedComponents: .date
            )
            .datePickerStyle(.wheel)
        }
    }
}

struct SexStepView: View {
    @Binding var selectedSex: String
    
    private let options = ["Male", "Female", "Other"]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            Text("What's your sex?")
                .font(.title)
                .fontWeight(.bold)
            
            Text("This helps us provide more accurate recommendations.")
                .foregroundColor(.secondary)
            
            ForEach(options, id: \.self) { option in
                Button(action: { selectedSex = option }) {
                    HStack {
                        Text(option)
                        Spacer()
                        if selectedSex == option {
                            Image(systemName: "checkmark")
                                .foregroundColor(.blue)
                        }
                    }
                    .padding()
                    .background(Color(.systemBackground))
                    .cornerRadius(8)
                    .shadow(radius: 2)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

struct HeightStepView: View {
    @State var height: Double = 170 // Default height in cm
    
    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            Text("How tall are you?")
                .font(.title)
                .fontWeight(.bold)
            
            Text("This helps us calculate your metrics accurately.")
                .foregroundColor(.secondary)
            
            // Add height picker or input field
            Text("Height picker placeholder")
        }
    }
}

struct GoalStepView: View {
    @State var selectedGoal: String = "Lose Weight"
    
    private let goals = [
        "Lose Weight",
        "Maintain Weight",
        "Gain Weight",
        "Build Muscle",
        "Improve Health"
    ]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            Text("What's your goal?")
                .font(.title)
                .fontWeight(.bold)
            
            Text("This helps us tailor your experience.")
                .foregroundColor(.secondary)
            
            ForEach(goals, id: \.self) { goal in
                Button(action: { selectedGoal = goal }) {
                    HStack {
                        Text(goal)
                        Spacer()
                        if selectedGoal == goal {
                            Image(systemName: "checkmark")
                                .foregroundColor(.blue)
                        }
                    }
                    .padding()
                    .background(Color(.systemBackground))
                    .cornerRadius(8)
                    .shadow(radius: 2)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

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
