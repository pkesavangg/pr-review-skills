import SwiftUI
import AppSyncPackage

/// A thin wrapper around `AppSyncView` that lives inside the app-code hierarchy so it can be
/// reused wherever scanning is required without importing `AppSyncPackage` at call-sites.
/// Pass-throughs the same callbacks.
public struct AppSyncScannerView: View {
    // MARK: ‑ Configuration
    public var showManualEntryButton: Bool = true
    public var initialZoom: CGFloat? = nil

    // MARK: ‑ Callbacks
    public let onClose: () -> Void
    public let onManualEntry: () -> Void
    public let onScanned: (BodyCompData) -> Void

    public init(
        showManualEntryButton: Bool = true,
        initialZoom: CGFloat? = 1,
        onClose: @escaping () -> Void,
        onManualEntry: @escaping () -> Void,
        onScanned: @escaping (BodyCompData) -> Void
    ) {
        self.showManualEntryButton = showManualEntryButton
        self.initialZoom = initialZoom
        self.onClose = onClose
        self.onManualEntry = onManualEntry
        self.onScanned = onScanned
    }

    // MARK: ‑ View
    public var body: some View {
        AppSyncView(
            showManualEntryButton: showManualEntryButton,
            initialZoom: initialZoom,
            onClose: onClose,
            onManualEntry: onManualEntry,
            onScanned: onScanned
        )
        .background(Color.black)
        .ignoresSafeArea()
    }
}

#Preview {
    AppSyncScannerView(onClose: {}, onManualEntry: {}, onScanned: { _ in })
}