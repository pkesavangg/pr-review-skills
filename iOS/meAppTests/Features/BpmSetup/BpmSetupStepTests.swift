///
///  BpmSetupStepTests.swift
///  meAppTests
///

@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct BpmSetupStepTests {

    // MARK: - defaultSteps

    @Test("defaultSteps starts with selectModel and includes all core steps")
    func defaultStepsStartsWithSelectModel() {
        let steps = BpmSetupStep.defaultSteps
        #expect(steps.first == .selectModel)
        #expect(steps.contains(.btPermission))
        #expect(steps.contains(.selectUser))
        #expect(steps.contains(.confirmUser))
        #expect(steps.contains(.complete))
        #expect(!steps.contains(.intro))
        #expect(!steps.contains(.powerSwitch))
    }

    @Test("preSelectedSteps starts with intro instead of selectModel")
    func preSelectedStepsStartsWithIntro() {
        let steps = BpmSetupStep.preSelectedSteps
        #expect(steps.first == .intro)
        #expect(!steps.contains(.selectModel))
    }

    // MARK: - steps(for:isPreSelected:) — 0636 flow

    @Test("0636 flow includes powerSwitch and confirmUser when preSelected is false")
    func sku0636FlowWithSelectModel() {
        let steps = BpmSetupStep.steps(for: "0636", preSelected: false)
        #expect(steps.first == .selectModel)
        #expect(steps.contains(.powerSwitch))
        #expect(steps.contains(.confirmUser))
        #expect(!steps.contains(.intro))
    }

    @Test("0636 flow starts with intro when preSelected is true")
    func sku0636FlowPreSelected() {
        let steps = BpmSetupStep.steps(for: "0636", preSelected: true)
        #expect(steps.first == .intro)
        #expect(steps.contains(.powerSwitch))
        #expect(steps.contains(.confirmUser))
    }

    @Test("0636 flow order is: first, btPermission, selectUser, powerSwitch, setUser, confirmUser, prePairing, scanning, nickname, paired, measureSetup, measureStart, complete")
    func sku0636FlowOrder() {
        let steps = BpmSetupStep.steps(for: "0636", preSelected: false)
        let expected: [BpmSetupStep] = [
            .selectModel, .btPermission, .selectUser, .powerSwitch, .setUser,
            .confirmUser, .prePairing, .scanning, .nickname, .paired,
            .measureSetup, .measureStart, .complete
        ]
        #expect(steps == expected)
    }

    // MARK: - steps(for:isPreSelected:) — 0604/0661 toggle flow

    @Test("0604 flow skips confirmUser when preSelected is false")
    func sku0604FlowWithSelectModel() {
        let steps = BpmSetupStep.steps(for: "0604", preSelected: false)
        #expect(steps.first == .selectModel)
        #expect(!steps.contains(.confirmUser))
        #expect(!steps.contains(.powerSwitch))
    }

    @Test("0661 flow skips confirmUser when preSelected is false")
    func sku0661FlowWithSelectModel() {
        let steps = BpmSetupStep.steps(for: "0661", preSelected: false)
        #expect(steps.first == .selectModel)
        #expect(!steps.contains(.confirmUser))
        #expect(!steps.contains(.powerSwitch))
    }

    @Test("0604 flow starts with intro when preSelected is true")
    func sku0604FlowPreSelected() {
        let steps = BpmSetupStep.steps(for: "0604", preSelected: true)
        #expect(steps.first == .intro)
        #expect(!steps.contains(.confirmUser))
    }

    @Test("0661 flow starts with intro when preSelected is true")
    func sku0661FlowPreSelected() {
        let steps = BpmSetupStep.steps(for: "0661", preSelected: true)
        #expect(steps.first == .intro)
        #expect(!steps.contains(.confirmUser))
    }

    @Test("0604 flow order skips confirmUser: first, btPermission, selectUser, setUser, prePairing, scanning, nickname, paired, measureSetup, measureStart, complete")
    func sku0604FlowOrder() {
        let steps = BpmSetupStep.steps(for: "0604", preSelected: false)
        let expected: [BpmSetupStep] = [
            .selectModel, .btPermission, .selectUser, .setUser,
            .prePairing, .scanning, .nickname, .paired,
            .measureSetup, .measureStart, .complete
        ]
        #expect(steps == expected)
    }

    // MARK: - steps(for:isPreSelected:) — default flow (0603, 0634, 0663)

    @Test("0603 default flow includes confirmUser, no powerSwitch")
    func sku0603DefaultFlow() {
        let steps = BpmSetupStep.steps(for: "0603", preSelected: false)
        #expect(steps.first == .selectModel)
        #expect(steps.contains(.confirmUser))
        #expect(!steps.contains(.powerSwitch))
    }

    @Test("0634 default flow includes confirmUser, no powerSwitch")
    func sku0634DefaultFlow() {
        let steps = BpmSetupStep.steps(for: "0634", preSelected: false)
        #expect(steps.contains(.confirmUser))
        #expect(!steps.contains(.powerSwitch))
    }

    @Test("0663 default flow includes confirmUser, no powerSwitch")
    func sku0663DefaultFlow() {
        let steps = BpmSetupStep.steps(for: "0663", preSelected: false)
        #expect(steps.contains(.confirmUser))
        #expect(!steps.contains(.powerSwitch))
    }

    @Test("0603 flow preSelected starts with intro")
    func sku0603PreSelectedStartsWithIntro() {
        let steps = BpmSetupStep.steps(for: "0603", preSelected: true)
        #expect(steps.first == .intro)
        #expect(!steps.contains(.selectModel))
        #expect(steps.contains(.confirmUser))
    }

    @Test("default flow order: first, btPermission, selectUser, setUser, confirmUser, prePairing, scanning, nickname, paired, measureSetup, measureStart, complete")
    func defaultFlowOrder() {
        let steps = BpmSetupStep.steps(for: "0603", preSelected: false)
        let expected: [BpmSetupStep] = [
            .selectModel, .btPermission, .selectUser, .setUser,
            .confirmUser, .prePairing, .scanning, .nickname,
            .paired, .measureSetup, .measureStart, .complete
        ]
        #expect(steps == expected)
    }

    @Test("unknown SKU uses default flow")
    func unknownSkuUsesDefaultFlow() {
        let steps = BpmSetupStep.steps(for: "9999", preSelected: false)
        #expect(steps.contains(.confirmUser))
        #expect(!steps.contains(.powerSwitch))
        #expect(steps.first == .selectModel)
    }
}
