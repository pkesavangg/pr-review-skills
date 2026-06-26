# Image optimization (SwiftUI)

Optional optimization. Suggest when you see large images decoded at full resolution (e.g. `UIImage(data:)` for thumbnails, or remote images shown small).

## Downsample before display
Decoding a full-res image into a small view wastes memory (decoded size = W×H×4 bytes, independent of file size). Downsample to the target pixel size:

```swift
import ImageIO

func downsampledImage(at url: URL, maxPixel: CGFloat, scale: CGFloat) -> UIImage? {
    let opts = [kCGImageSourceShouldCache: false] as CFDictionary
    guard let src = CGImageSourceCreateWithURL(url as CFURL, opts) else { return nil }
    let thumbOpts = [
        kCGImageSourceCreateThumbnailFromImageAlways: true,
        kCGImageSourceShouldCacheImmediately: true,
        kCGImageSourceCreateThumbnailWithTransform: true,
        kCGImageSourceThumbnailMaxPixelSize: maxPixel * scale
    ] as CFDictionary
    guard let cg = CGImageSourceCreateThumbnailAtIndex(src, 0, thumbOpts) else { return nil }
    return UIImage(cgImage: cg)
}
```

## Guidance
- For remote images, prefer `AsyncImage` (iOS 15+); for caching/control use the project's image layer if one exists.
- Decode/downsample **off the main thread** (`Task.detached` / background), then assign on `@MainActor` — consistent with the project's concurrency rules (`swift-concurrency`).
- Use `.resizable().scaledToFill()` + `.frame(...)` + `.clipped()` for fixed-size thumbnails; downsampling complements, not replaces, frame sizing.
- Set `.interpolation(.medium)` for downscaled images to avoid aliasing.
- Don't optimize prematurely — only when a measured memory/scroll-perf problem exists (see `performance-patterns.md`).
