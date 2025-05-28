import Foundation
import SwiftData

@Model
final class Scale {
    @Attribute(.unique) var id: String
    var scaleType: String? // was 'type', now matches table
    var bodyComp: Bool?    // new field for body composition support
    
    init(id: String, scaleType: String?, bodyComp: Bool?) {
        self.id = id
        self.scaleType = scaleType
        self.bodyComp = bodyComp
    }
}
