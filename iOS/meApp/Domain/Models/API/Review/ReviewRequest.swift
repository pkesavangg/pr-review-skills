import Foundation

/// The kind of review being submitted to the unified `POST /v3/review/` endpoint.
/// The server routes `app` → `app_review_report` and `scale`/`monitor` → `scale_review_report`.
enum ReviewType: String, Codable, Sendable {
    case app
    case scale
    case monitor
}

/// Status of a review submission. App reviews additionally support `iOS`; scale/monitor reviews
/// use the `exit*`/`reviewed`/`feedback` subset. A `rating` is required for every status except
/// `exitA`.
enum ReviewStatus: String, Codable, Sendable {
    case ios = "iOS"
    case exitA
    case exitB
    case exitC
    case reviewed
    case feedback
}

/// Request body for `POST /v3/review/` — the Me App 2.0 unified review endpoint that replaces the
/// legacy `POST /v3/review/app` and `POST /v3/review/scale` endpoints. Responds with 204 No Content.
struct ReviewRequest: Codable, Sendable {
    /// `app`, `scale`, or `monitor`.
    let reviewType: String
    /// Device SKU — required when `reviewType` is `scale` or `monitor`.
    let sku: String?
    /// Review status (see `ReviewStatus`).
    let status: String
    /// Numeric rating — required unless `status` is `exitA`.
    let rating: Int?
    let feedback: String?
    /// Account flag reference that triggered the review prompt.
    let flagId: String?

    init(
        reviewType: ReviewType,
        status: ReviewStatus,
        rating: Int? = nil,
        sku: String? = nil,
        feedback: String? = nil,
        flagId: String? = nil
    ) {
        self.reviewType = reviewType.rawValue
        self.status = status.rawValue
        self.rating = rating
        self.sku = sku
        self.feedback = feedback
        self.flagId = flagId
    }
}
