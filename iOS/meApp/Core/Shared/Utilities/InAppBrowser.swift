import SwiftUI
import SafariServices

/// A SwiftUI wrapper for presenting an in-app browser using SFSafariViewController.
/// Use this view to present a web page modally within your app.
struct SafariView: UIViewControllerRepresentable {
    /// The URL to open in the browser.
    let url: URL
    /// Optional completion handler called when the browser is dismissed.
    let completion: (() -> Void)?
    
    func makeUIViewController(context: Context) -> SFSafariViewController {
        let safariVC = SFSafariViewController(url: url)
        safariVC.delegate = context.coordinator
        return safariVC
    }
    
    func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {}
    
    func makeCoordinator() -> Coordinator {
        Coordinator(completion: completion)
    }
}

#Preview {
    // Example usage in a preview
    SafariView(url: URL(string: "https://greatergoods.com/service")!, completion: {
        print("Browser was dismissed")
    })
}

// MARK: - Usage Example
/*
// Example: Using the .inAppBrowser view modifier in a SwiftUI view
import SwiftUI

struct ContentView: View {
    @State private var showBrowser = false
    var body: some View {
        Button("Open Browser") {
            showBrowser = true
        }
        .inAppBrowser(
            url: URL(string: "https://example.com")!,
            isPresented: $showBrowser
        ) {
            print("Browser was dismissed")
        }
    }
}
*/
