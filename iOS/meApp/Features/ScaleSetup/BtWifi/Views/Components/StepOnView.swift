//
//  StepOnView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 15/07/25.
//

import SwiftUI
import WebKit

/// Alternative WebKit-based GIF view for better size control
struct WebGifView: UIViewRepresentable {
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

/// A view that displays an animated GIF from the app bundle
struct AnimatedGifView: UIViewRepresentable {
    let gifName: String
    
    func makeUIView(context: Context) -> UIImageView {
        let imageView = UIImageView()
        imageView.contentMode = .scaleAspectFit
        imageView.clipsToBounds = true
        
        // Important: Set these to allow SwiftUI to control the size
        imageView.setContentHuggingPriority(.defaultLow, for: .horizontal)
        imageView.setContentHuggingPriority(.defaultLow, for: .vertical)
        imageView.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        imageView.setContentCompressionResistancePriority(.defaultLow, for: .vertical)
        
        if let gifPath = Bundle.main.path(forResource: gifName, ofType: "gif"),
           let gifData = NSData(contentsOfFile: gifPath),
           let source = CGImageSourceCreateWithData(gifData, nil) {
            
            var images: [UIImage] = []
            var duration: TimeInterval = 0
            
            let imageCount = CGImageSourceGetCount(source)
            for i in 0..<imageCount {
                if let cgImage = CGImageSourceCreateImageAtIndex(source, i, nil) {
                    let image = UIImage(cgImage: cgImage)
                    images.append(image)
                    
                    // Get frame duration
                    if let properties = CGImageSourceCopyPropertiesAtIndex(source, i, nil) as? [String: Any],
                       let gifProperties = properties[kCGImagePropertyGIFDictionary as String] as? [String: Any],
                       let frameDuration = gifProperties[kCGImagePropertyGIFDelayTime as String] as? Double {
                        duration += frameDuration
                    }
                }
            }
            
            if !images.isEmpty {
                let animatedImage = UIImage.animatedImage(with: images, duration: duration)
                imageView.image = animatedImage
            }
        }
        
        return imageView
    }
    
    func updateUIView(_ uiView: UIImageView, context: Context) {
        // No updates needed
    }
}

struct StepOnView: View {
    @Environment(\.appTheme) private var theme
    private let lang = BtWifiScaleSetupStrings.StepOnStrings.self
    
    var body: some View {
        VStack(spacing: .spacingLG) {
            VStack(spacing: .spacingXS) {
                Text(lang.title)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                
                Text(lang.subtitle)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textHeading)
            }
            WebGifView(gifName: "stepOn")
                .frame(height: 211)
                .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: .radiusSM))
                .padding(.horizontal, .spacingSM)
        }
        .padding(.bottom, .spacingLG)
    }
}

struct GifLoadingView: View {
    var body: some View {
        WebGifView(gifName: "stepOn")
            .frame(height: 211)
            .frame(maxWidth: .infinity)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .padding(.horizontal)
    }
}

#Preview {
    StepOnView()
}
