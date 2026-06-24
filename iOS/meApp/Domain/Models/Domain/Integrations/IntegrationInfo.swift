struct IntegrationInfo: Codable, Equatable {
    let type: IntegrationType
    let isIntegrated: Bool
    var assignedTo: String?
    var deIntegrated: String?
}
