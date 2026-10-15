//
//  SignInModelTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

@MainActor
struct SignInModelTests {
    private let server = ScriptedServer()
    private let secrets = FakeSecretStore()

    private func makeModel(onSignedIn: @escaping () async -> Void = {}) -> SignInModel {
        let account = AccountStore(secrets: secrets, client: AuthClient(baseURL: server.baseURL, session: server.session))
        return SignInModel(account: account, onSignedIn: onSignedIn)
    }

    private func serve(code: String = "123456") {
        server.handler = { request in
            switch request.path {
            case "/api/auth/email-code": .empty(204)
            case "/api/auth/verify":
                request.json["code"] as? String == code ? tokenReply(access: "A1", refresh: "R1") : .problem(401, code: "invalid_code")
            default: .empty(404)
            }
        }
    }

    // MARK: - Step 1

    @Test func startsOnTheEmailStepAndOnlyAcceptsAnEmail() {
        let model = makeModel()
        #expect(model.step == .email)
        #expect(model.canSendCode == false)

        model.email = "learner"
        #expect(model.canSendCode == false)
        model.email = "learner@example"
        #expect(model.canSendCode == false)
        model.email = "  learner@example.com "
        #expect(model.canSendCode)
    }

    @Test func sendingTheCodeMovesToTheCodeStep() async {
        serve()
        let model = makeModel()
        model.email = " learner@example.com "

        await model.sendCode()

        #expect(model.step == .code)
        #expect(model.sentTo == "learner@example.com")
        #expect(server.requests.first?.json["email"] as? String == "learner@example.com")
    }

    @Test func aServerErrorStaysOnTheEmailStepWithItsMessage() async {
        server.handler = { _ in .problem(400, code: "invalid_email", detail: "Enter a valid email address.") }
        let model = makeModel()
        model.email = "learner@example.com"

        await model.sendCode()

        #expect(model.step == .email)
        #expect(model.errorMessage == "Enter a valid email address.")
    }

    @Test func tooManyCodesShowsMinutes() async {
        server.handler = { _ in .problem(429, code: "rate_limited", headers: ["Retry-After": "900"]) }
        let model = makeModel()
        model.email = "learner@example.com"

        await model.sendCode()

        #expect(model.step == .email)
        #expect(model.errorMessage == "Too many requests. Try again in 15 minutes.")
    }

    @Test func noInvalidEmailIsSent() async {
        serve()
        let model = makeModel()
        model.email = "nope"

        await model.sendCode()

        #expect(server.requests.isEmpty)
        #expect(model.step == .email)
    }

    // MARK: - Step 2

    /// Mail fills all 6 digits in one go. The code must be saved at once (not later in a Task),
    /// or SwiftUI reads the old empty value back and the filled code disappears.
    @Test func aFullCodeFromMailIsSavedAtOnce() {
        let model = makeModel()
        #expect(model.setCode("482 913") == true)
        #expect(model.code == "482913")
        #expect(model.setCode("12") == false)
        #expect(model.code == "12")
    }

    @Test func theCodeKeepsDigitsOnlyAndAtMostSix() async {
        serve()
        let model = makeModel()
        model.email = "learner@example.com"
        await model.sendCode()

        await model.updateCode("12 a3")
        #expect(model.code == "123")
        #expect(server.requests(to: "/api/auth/verify").isEmpty)
    }

    @Test func theSixthDigitChecksTheCodeAndSignsIn() async {
        serve()
        var signedInCalls = 0
        let model = makeModel(onSignedIn: { signedInCalls += 1 })
        model.email = "learner@example.com"
        await model.sendCode()

        await model.updateCode("123456")

        #expect(model.didSignIn)
        #expect(signedInCalls == 1)
        #expect(server.requests(to: "/api/auth/verify").count == 1)
        #expect(secrets.secret(forKey: "session") != nil)
    }

    @Test func mailCanFillAllSixDigitsAtOnce() async {
        serve()
        let model = makeModel()
        model.email = "learner@example.com"
        await model.sendCode()

        await model.updateCode("123 456")

        #expect(model.didSignIn)
    }

    @Test func aWrongCodeShowsAMessageAndClearsTheBoxes() async {
        serve()
        let model = makeModel()
        model.email = "learner@example.com"
        await model.sendCode()

        await model.updateCode("111111")

        #expect(model.didSignIn == false)
        #expect(model.code == "")
        #expect(model.errorMessage == "That code is wrong or has expired. Try again, or send a new code.")
        #expect(model.step == .code)
    }

    @Test func afterFiveWrongTriesTheMessageSaysToSendANewCode() async {
        serve()
        let model = makeModel()
        model.email = "learner@example.com"
        await model.sendCode()

        for _ in 0..<SignInModel.maxWrongTries {
            await model.updateCode("111111")
        }

        #expect(model.errorMessage == "Too many tries. Send a new code.")
    }

    @Test func sendingANewCodeClearsTheErrorAndTheTries() async {
        serve()
        let model = makeModel()
        model.email = "learner@example.com"
        await model.sendCode()
        await model.updateCode("111111")

        await model.sendNewCode()

        #expect(model.errorMessage == nil)
        #expect(model.noticeMessage == "We sent a new code.")
        #expect(model.wrongTries == 0)
        #expect(server.requests(to: "/api/auth/email-code").count == 2)
    }
}
