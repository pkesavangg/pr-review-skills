//
//  Typography.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 27/05/25.
//
import Foundation
import SwiftUI

// SwiftUI Font Weight to CSS Equivalent values:
// .ultraLight -> CSS: 100
// .thin -> CSS: 200
// .light -> CSS: 300
// .regular -> CSS: 400 (normal)
// .medium -> CSS: 500
// .semibold -> CSS: 600
// .bold -> CSS: 700
// .heavy -> CSS: 800
// .black -> CSS: 900

extension Font {
    // MARK: - Typography System - Open Sans
    
    // Heading Styles
    static var heading1: Font {
        return Font.system(size: 60, weight: .heavy) // Extra Bold
    }
    
    static var heading2: Font {
        return Font.system(size: 50, weight: .heavy) // Extra Bold
    }
    
    static var heading3: Font {
        return Font.system(size: 36, weight: .bold)
    }
    
    static var heading4: Font {
        return Font.system(size: 24, weight: .bold)
    }
    
    static var heading5: Font {
        return Font.system(size: 16, weight: .bold)
    }
    
    // Sub Heading Styles
    static var subHeading1: Font {
        return Font.system(size: 16, weight: .regular)
    }
    
    static var subHeading2: Font {
        return Font.system(size: 14, weight: .regular)
    }
    
    // Body Text Styles
    static var body1: Font {
        return Font.system(size: 20, weight: .regular)
    }
    
    static var body2: Font {
        return Font.system(size: 16, weight: .regular)
    }
    
    static var body3: Font {
        return Font.system(size: 14, weight: .regular)
    }
    
    // Link Styles
    static var link1: Font {
        return Font.system(size: 16, weight: .semibold)
    }
    
    static var link2: Font {
        return Font.system(size: 12, weight: .semibold)
    }
    
    // Button Styles
    static var button1: Font {
        return Font.system(size: 16, weight: .semibold)
    }
    
    static var button2: Font {
        return Font.system(size: 14, weight: .semibold)
    }
}

// MARK: - Text Extensions
extension Text {
    // Typography System Extensions
    func fontHeading1() -> Text {
        return self.font(.heading1)
    }
    
    func fontHeading2() -> Text {
        return self.font(.heading2)
    }
    
    func fontHeading3() -> Text {
        return self.font(.heading3)
    }
    
    func fontHeading4() -> Text {
        return self.font(.heading4)
    }
    
    func fontHeading5() -> Text {
        return self.font(.heading5)
    }
    
    func fontSubHeading1() -> Text {
        return self.font(.subHeading1)
    }
    
    func fontSubHeading2() -> Text {
        return self.font(.subHeading2)
    }
    
    func fontBody1() -> Text {
        return self.font(.body1)
    }
    
    func fontBody2() -> Text {
        return self.font(.body2)
    }
    
    func fontBody3() -> Text {
        return self.font(.body3)
    }
    
    func fontLink1() -> Text {
        return self.font(.link1)
    }
    
    func fontLink2() -> Text {
        return self.font(.link2)
    }
    
    func fontButton1() -> Text {
        return self.font(.button1)
    }
    
    func fontButton2() -> Text {
        return self.font(.button2)
    }
    
    // Custom OpenSans Font Extension
    func fontOpenSans(_ textStyle: CustomTextStyle) -> Text {
        return self.font(.custom("OpenSans-Regular", size: textStyle.size))
                  .fontWeight(textStyle.weight)
    }
}

// MARK: - Typography Helper Struct

struct Typography {
    static let heading1 = Font.heading1
    static let heading2 = Font.heading2
    static let heading3 = Font.heading3
    static let heading4 = Font.heading4
    static let heading5 = Font.heading5
    
    static let subHeading1 = Font.subHeading1
    static let subHeading2 = Font.subHeading2
    
    static let body1 = Font.body1
    static let body2 = Font.body2
    static let body3 = Font.body3
    
    static let link1 = Font.link1
    static let link2 = Font.link2
    
    static let button1 = Font.button1
    static let button2 = Font.button2
}

// MARK: - Usage Examples
/*
// System fonts:
Text("Large Heading").font(.heading1)
Text("Medium Heading").fontHeading3()
Text("Body text").font(.body2)
Text("Link text").fontLink1().foregroundColor(.blue)
Text("Button text").fontButton1()

// OpenSans custom font (handles all weights automatically):
Text("Custom Heading").fontOpenSans(.heading1) // 60pt, Extra Bold
Text("Regular Body").fontOpenSans(.body2)      // 16pt, Regular
Text("Semibold Link").fontOpenSans(.link1)     // 16pt, Semibold

// Using Typography helper:
Text("Helper Usage").font(Typography.heading2)
*/
