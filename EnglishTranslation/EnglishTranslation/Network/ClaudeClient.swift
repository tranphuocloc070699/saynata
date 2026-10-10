//
//  ClaudeClient.swift
//  EnglishTranslation
//

import Foundation

/// The body of a request to the Claude Messages API.
private struct MessagesRequest: Encodable {
    let model: String
    let maxTokens: Int
    let system: String
    let messages: [Message]

    /// One chat message.
    struct Message: Encodable {
        let role: String
        let content: String
    }

    /// Claude uses snake_case JSON keys.
    enum CodingKeys: String, CodingKey {
        case model, system, messages
        case maxTokens = "max_tokens"
    }
}

/// The part of the Claude reply we need: a list of content blocks.
private struct MessagesResponse: Decodable {
    let content: [Block]

    /// One block of the reply. Only `"text"` blocks have `text`.
    struct Block: Decodable {
        let type: String
        let text: String?
    }
}

/// Talks to the Claude Messages API.
/// Docs: https://docs.claude.com/en/api/messages
struct ClaudeClient: ChatCompleting {
    let apiKey: String
    let model: String

    private static let endpoint = URL(string: "https://api.anthropic.com/v1/messages")!

    /// Sends the prompts and returns the model's raw reply. Throws `LLMError` or a `URLError`.
    func reply(system: String, user: String) async throws -> String {
        var request = URLRequest(url: Self.endpoint)
        request.httpMethod = "POST"
        request.setValue(apiKey, forHTTPHeaderField: "x-api-key")
        request.setValue("2023-06-01", forHTTPHeaderField: "anthropic-version")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(
            MessagesRequest(
                model: model,
                maxTokens: 1024,
                system: system,
                messages: [.init(role: "user", content: user)]))

        let (data, response) = try await URLSession.shared.data(for: request)

        let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard statusCode == 200 else {
            throw LLMError.badStatus(statusCode)
        }

        let decoded = try JSONDecoder().decode(MessagesResponse.self, from: data)
        guard let text = decoded.content.first(where: { $0.type == "text" })?.text else {
            throw LLMError.emptyReply
        }
        return text
    }
}
