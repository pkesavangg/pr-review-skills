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
    var subdirectory: String?
    var width: CGFloat = 370  // default width
    var height: CGFloat = 211 // default height
    var verticalAlignment: GifVerticalAlignment = .center
    var cornerRadius: CGFloat = 0

    init(
        gifName: String,
        subdirectory: String? = nil,
        width: CGFloat = 370,
        height: CGFloat = 211,
        verticalAlignment: GifVerticalAlignment = .center,
        cornerRadius: CGFloat = 0
    ) {
        self.gifName = gifName
        self.subdirectory = subdirectory
        self.width = width
        self.height = height
        self.verticalAlignment = verticalAlignment
        self.cornerRadius = cornerRadius
    }

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView()
        webView.scrollView.isScrollEnabled = false
        webView.backgroundColor = .clear
        webView.isOpaque = false
        webView.isUserInteractionEnabled = false
        if cornerRadius > 0 {
            webView.clipsToBounds = true
            webView.layer.cornerRadius = cornerRadius
        }
        loadGif(in: webView)
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        loadGif(in: uiView)
    }

    private func loadGif(in webView: WKWebView) {
        let gifPath = resolvedGifPath()
        if let gifPath {
            let url = URL(fileURLWithPath: gifPath)
            do {
                let base64Data = try Data(contentsOf: url).base64EncodedString()
                let html = """
                <html style="height:100%;">
                    <head><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                    <body style="margin:0; padding:0; background:transparent; height:100%; overflow:hidden;">
                        <img
                            src="data:image/gif;base64,\(base64Data)"
                            style="width:100%; height:100%; object-fit:contain;
                            object-position:\(verticalAlignment.objectPosition);
                            display:block;"
                        />
                    </body>
                </html>
                """
                webView.loadHTMLString(html, baseURL: nil)
            } catch {
                LoggerService.shared.log(level: .error, tag: "GifView", message: "Failed to load GIF: \(error.localizedDescription)")
            }
        }
    }

    private func resolvedGifPath() -> String? {
        if let subdirectory,
           let subdirectoryPath = Bundle.main.path(forResource: gifName, ofType: "gif", inDirectory: subdirectory) {
            return subdirectoryPath
        }

        return Bundle.main.path(forResource: gifName, ofType: "gif")
    }
}
