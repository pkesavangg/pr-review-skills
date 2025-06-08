import SwiftUI

class BasicProfileForm: ObservableForm {
    var name = FormControl("", validators: [.required])
    var email = FormControl("", validators: [.email, .minLength(5)])
    var age = FormControl(2, validators: [.min(2)])
    var rememberMe = FormControl(false, validators: [.requiredTrue])
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
