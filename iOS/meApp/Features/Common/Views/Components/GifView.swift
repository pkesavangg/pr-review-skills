//
//  GifView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 16/07/25.
//


import SwiftUI
import WebKit

/// Alternative WebKit-based GIF view for better size control
struct GifView: UIViewRepresentable {
    let gifName: String
    var width: CGFloat = 370  // default width
    var height: CGFloat = 211 // default height
    /// Fires once after the underlying `WKWebView` finishes rendering the GIF HTML.
    /// Lets callers defer showing surrounding UI until the GIF is actually on screen.
    var onLoaded: (() -> Void)? = nil

    func makeCoordinator() -> Coordinator {
        Coordinator(onLoaded: onLoaded)
    }

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView()
        webView.scrollView.isScrollEnabled = false
        webView.backgroundColor = .clear
        webView.isOpaque = false
        webView.isUserInteractionEnabled = false
        webView.navigationDelegate = context.coordinator
        loadGif(in: webView)
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        context.coordinator.onLoaded = onLoaded
        loadGif(in: uiView)
    }

    private func loadGif(in webView: WKWebView) {
        if let gifPath = Bundle.main.path(forResource: gifName, ofType: "gif") {
            let url = URL(fileURLWithPath: gifPath)
            do {
                let base64Data = try Data(contentsOf: url).base64EncodedString()
                let html = """
                <html>
                    <head><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                    <body style="margin:0; padding:0; background: transparent;">
                        <img src="data:image/gif;base64,\(base64Data)" width="\(Int(width))" height="\(Int(height))"/>
                    </body>
                </html>
                """
                webView.loadHTMLString(html, baseURL: nil)
            } catch {
                LoggerService.shared.log(level: .error, tag: "GifView", message: "Failed to load GIF: \(error.localizedDescription)")
            }
        }
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        var onLoaded: (() -> Void)?
        private var hasFired = false

        init(onLoaded: (() -> Void)?) {
            self.onLoaded = onLoaded
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            guard !hasFired else { return }
            hasFired = true
            onLoaded?()
        }
    }
}

/// Renders the given GIF in a hidden, off-screen `WKWebView` so callers can
/// defer presenting UI until the GIF has actually been rendered by WebKit.
/// After completion, both the OS-level file cache and the shared WebKit
/// content process are warm, so a subsequent in-modal `GifView` renders quickly.
///
/// Hold a strong reference to the instance until `preload(completion:)`
/// invokes its completion handler — the preloader cleans up its hidden views
/// before signalling.
@MainActor
final class GifPreloader: NSObject, WKNavigationDelegate {
    private let gifName: String
    private var completion: (() -> Void)?
    private var webView: WKWebView?
    private var hostContainer: UIView?
    private var hasFinished = false
    private var timeoutTask: Task<Void, Never>?
    private let timeoutSeconds: TimeInterval

    init(gifName: String, timeoutSeconds: TimeInterval = 3.0) {
        self.gifName = gifName
        self.timeoutSeconds = timeoutSeconds
        super.init()
    }

    /// Begins preloading. `completion` is invoked exactly once on the main actor,
    /// either when WebKit reports `didFinish` or when the timeout elapses.
    func preload(completion: @escaping () -> Void) {
        self.completion = completion

        guard let window = Self.keyWindow() else {
            // No window available — skip preload and fall back to immediate completion.
            finish()
            return
        }

        let webView = WKWebView()
        webView.scrollView.isScrollEnabled = false
        webView.backgroundColor = .clear
        webView.isOpaque = false
        webView.isUserInteractionEnabled = false
        webView.navigationDelegate = self
        self.webView = webView

        // Off-screen container — WebKit only renders attached views.
        let container = UIView(frame: CGRect(x: -10_000, y: -10_000, width: 1, height: 1))
        container.isUserInteractionEnabled = false
        container.alpha = 0.001
        container.addSubview(webView)
        webView.frame = container.bounds
        window.addSubview(container)
        hostContainer = container

        loadHTML(in: webView)
        scheduleTimeout()
    }

    private func loadHTML(in webView: WKWebView) {
        guard let path = Bundle.main.path(forResource: gifName, ofType: "gif") else {
            LoggerService.shared.log(level: .error, tag: "GifPreloader", message: "GIF resource not found: \(gifName)")
            finish()
            return
        }
        do {
            let base64 = try Data(contentsOf: URL(fileURLWithPath: path)).base64EncodedString()
            let html = """
            <html>
                <head><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                <body style="margin:0; padding:0; background: transparent;">
                    <img src="data:image/gif;base64,\(base64)" width="1" height="1"/>
                </body>
            </html>
            """
            webView.loadHTMLString(html, baseURL: nil)
        } catch {
            LoggerService.shared.log(level: .error, tag: "GifPreloader", message: "Failed to read GIF data: \(error.localizedDescription)")
            finish()
        }
    }

    private func scheduleTimeout() {
        let seconds = timeoutSeconds
        timeoutTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
            guard !Task.isCancelled else { return }
            self?.finish()
        }
    }

    private func finish() {
        guard !hasFinished else { return }
        hasFinished = true
        timeoutTask?.cancel()
        timeoutTask = nil
        webView?.navigationDelegate = nil
        webView?.removeFromSuperview()
        webView = nil
        hostContainer?.removeFromSuperview()
        hostContainer = nil
        let pending = completion
        completion = nil
        pending?()
    }

    private static func keyWindow() -> UIWindow? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first(where: { $0.isKeyWindow })
    }

    nonisolated func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        Task { @MainActor [weak self] in self?.finish() }
    }
}
