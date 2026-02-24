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

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView()
        webView.scrollView.isScrollEnabled = false
        webView.backgroundColor = .clear
        webView.isOpaque = false
        webView.isUserInteractionEnabled = false
        loadGif(in: webView)
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
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
}
