struct IntegrationInfo: Codable, Equatable {
    let type: IntegrationType
    let isIntegrated: Bool
    let assignedTo: String?
    let deIntegrated: String?
}