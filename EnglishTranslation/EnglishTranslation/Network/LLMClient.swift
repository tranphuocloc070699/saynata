//
//  LLMClient.swift
//  EnglishTranslation
//
//  Created by Trần Phước Lộc on 7/10/26.
//
import Foundation

/// The body of an OpenAI-compatible chat request.
private struct ChatRequest: Encodable {
    let model: String
    let messages: [Message]
    let responseFormat = ResponseFormat()

    /// One chat message.
    struct Message: Encodable {
        let role: String
        let content: String
    }

    /// Asks the model for valid JSON. Works on DeepSeek and OpenAI.
    struct ResponseFormat: Encodable {
        let type = "json_object"
    }

    /// The API uses snake_case JSON keys.
    enum CodingKeys: String, CodingKey {
        case model, messages
        case responseFormat = "response_format"
    }
}

/// The part of an OpenAI-compatible reply we need.
private struct ChatResponse: Decodable {
    let choices: [Choice]

    /// One answer. We only ask for one.
    struct Choice: Decodable {
        let message: Message
    }

    /// The answer text.
    struct Message: Decodable {
        let content: String
    }
}

/// What can go wrong when talking to an LLM API.
enum LLMError: LocalizedError {
    case badStatus(Int)
    case emptyReply
    case badFormat

    var errorDescription: String? {
        switch self {
        case let .badStatus(code): "The server answered with status \(code)."
        case .emptyReply: "The model returned no text."
        case .badFormat: "The model's answer was not in the expected format. Try again."
        
        }
    }
}

/// Talks to an OpenAI-compatible chat API (DeepSeek, OpenAI).
struct LLMClient: ChatCompleting {
    let apiKey: String
    /// The API root, for example `https://api.deepseek.com`.
    let baseURL: URL
    let model: String

    /// Sends the prompts and returns the model's raw reply. Throws `LLMError` or a `URLError`.
    func reply(system: String, user: String) async throws -> String {
        var request = URLRequest(url: baseURL.appending(path: "chat/completions"))
        request.httpMethod = "POST"
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(
            ChatRequest(
                model: model,
                messages: [
                    .init(role: "system", content: system),
                    .init(role: "user", content: user),
                ]))

        let (data, response) = try await URLSession.shared.data(for: request)

        let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard statusCode == 200 else {
            throw LLMError.badStatus(statusCode)
        }

        let decoded = try JSONDecoder().decode(ChatResponse.self, from: data)
        guard let text = decoded.choices.first?.message.content else {
            throw LLMError.emptyReply
        }
        return text
    }
}
