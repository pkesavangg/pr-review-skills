import AuthenticationServices
import SafariServices
import SwiftUI

// MARK: - OAuthWebSession

/// Presents OAuth flows using ASWebAuthenticationSession with an ephemeral (private) session.
/// Unlike SFSafariViewController, this shares no cookies with Safari or between sessions,
/// so reconnecting Fitbit/MFP always shows a fresh login/account-selection screen.
@MainActor
final class OAuthWebSession: NSObject, ASWebAuthenticationPresentationContextProviding, ObservableObject {
    private var session: ASWebAuthenticationSession?

    /// Starts the OAuth web session for the given URL.
    /// `completion` is called when the user dismisses the session (either after auth or via cancel).
    func start(url: URL, completion: @escaping () -> Void) {
        let session = ASWebAuthenticationSession(
            url: url,
            callbackURLScheme: nil  // server-side redirect; no deep-link callback needed
        ) { _, _ in
            // Fired on both success-dismiss and user cancel — in both cases the flow is done.
            completion()
        }
        session.prefersEphemeralWebBrowserSession = true
        session.presentationContextProvider = self
        session.start()
        self.session = session
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }
}

// MARK: - SafariView

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
    
    func makeCoordinator() -> BrowserCoordinator {
        BrowserCoordinator(completion: completion)
    }
}

#Preview {
    // Example usage in a preview
    SafariView(url: URL(string: "https://greatergoods.com/service") ?? URL(fileURLWithPath: "/")) { }
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
        ) { }
    }
}
*/
