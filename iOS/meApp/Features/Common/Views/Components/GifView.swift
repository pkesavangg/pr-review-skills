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
    
    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView()
        webView.scrollView.isScrollEnabled = false
        webView.backgroundColor = .clear
        webView.isOpaque = false
        webView.isUserInteractionEnabled = false
        
        if let gifPath = Bundle.main.path(forResource: gifName, ofType: "gif"),
           let gifData = NSData(contentsOfFile: gifPath) {
            webView.load(gifData as Data, mimeType: "image/gif", characterEncodingName: "UTF-8", baseURL: Bundle.main.bundleURL)
        }
        
        return webView
    }
    
    func updateUIView(_ uiView: WKWebView, context: Context) {
        // No updates needed
    }
}