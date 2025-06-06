import SwiftUI
import SafariServices

private struct SafariView: UIViewControllerRepresentable {
    let url: URL
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

    class Coordinator: NSObject, SFSafariViewControllerDelegate {
        let completion: (() -> Void)?

        init(completion: (() -> Void)?) {
            self.completion = completion
        }

        func safariViewControllerDidFinish(_ controller: SFSafariViewController) {
            completion?()
        }
    }
}

#Preview {
    SafariView(url: URL(string: "https://greatergoods.com/service")!, completion: {})
}


// MARK: inAppBrowser with FullScreen cover
public extension View {
    /// Presents an in-app browser with the given URL
    /// - Parameters:
    ///   - url: The URL to open
    ///   - isPresented: Binding to control the presentation
    ///   - presentationStyle: The presentation style for the browser
    ///   - completion: Optional completion handler called when the browser is dismissed
    func inAppBrowser(url: URL,
                     isPresented: Binding<Bool>,
                     presentationStyle: UIModalPresentationStyle = .automatic,
                     completion: (() -> Void)? = nil) -> some View {
        self.fullScreenCover(isPresented: isPresented) {
            SafariView(url: url, completion: completion)
        }
    }
}

// MARK: inAppBrowser with Custom R-L sliding open

struct CustomSafariPresenter: UIViewControllerRepresentable {
    let url: URL
    let isPresented: Binding<Bool>
    let completion: (() -> Void)?

    func makeUIViewController(context: Context) -> UIViewController {
        return UIViewController() // Acts as a host for presentation
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        if isPresented.wrappedValue && context.coordinator.safariVC == nil {
            let safariVC = SFSafariViewController(url: url)
            safariVC.delegate = context.coordinator
            safariVC.modalPresentationStyle = .fullScreen
            safariVC.modalTransitionStyle = .coverVertical // Default. We'll change this below

            // Add custom animation
            if let presenter = uiViewController.presentingViewController {
                presenter.dismiss(animated: false)
            }
            uiViewController.present(safariVC, animated: true, completion: nil)

            context.coordinator.safariVC = safariVC
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(isPresented: isPresented, completion: completion)
    }

    class Coordinator: NSObject, SFSafariViewControllerDelegate {
        var isPresented: Binding<Bool>
        var completion: (() -> Void)?
        weak var safariVC: SFSafariViewController?

        init(isPresented: Binding<Bool>, completion: (() -> Void)?) {
            self.isPresented = isPresented
            self.completion = completion
        }

        func safariViewControllerDidFinish(_ controller: SFSafariViewController) {
            isPresented.wrappedValue = false
            completion?()
        }
    }
}

public extension View {
    func customInAppBrowser(url: URL,
                            isPresented: Binding<Bool>,
                            completion: (() -> Void)? = nil) -> some View {
        self.background(
            CustomSafariPresenter(url: url, isPresented: isPresented, completion: completion)
        )
    }
}


// MARK: - Usage Example
/*
 // Using the utility class directly:
 InAppBrowser.shared.open(url: URL(string: "https://example.com")!) {
     print("Browser was dismissed")
 }
 
 // Using the SwiftUI view modifier:
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
