/// Object representing the viewed status for all permissions.
struct PermissionsViewedStatus: Codable, Equatable {
    var status: [PermissionType: Bool]

    init(status: [PermissionType: Bool] = [:]) {
        self.status = status
    }

    subscript(type: PermissionType) -> Bool {
        get { status[type] ?? false }
        set { status[type] = newValue }
    }
}