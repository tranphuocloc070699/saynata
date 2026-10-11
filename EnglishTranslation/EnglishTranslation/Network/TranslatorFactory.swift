//
//  TranslatorFactory.swift
//  EnglishTranslation
//

import Foundation

/// Builds the real translator for each `Provider`, with its key from `Secrets.xcconfig`.
enum TranslatorFactory {
    /// Returns the translator for `provider`. Throws if its API key is missing.
    static func makeTranslator(for provider: Provider) throws -> any Translating {
        switch provider {
        case .server: try makeBackendClient()
        case .deepSeek: try makeDeepSeekClient()
        case .openAI: try makeOpenAIClient()
        case .claude: try makeClaudeClient()
        }
    }

    /// Our Java backend. The URL comes from `BackendBaseURL` in Info.plist.
    static func makeBackendClient() throws -> any Translating {
        BackendClient(baseURL: try AppConfig.backendBaseURL(), cache: ResultCache.shared)
    }

    /// The bundled dictionary file. Opened once; nil if it cannot be opened.
    private static let bundledDictionary = try? DictionaryStore.bundled()

    /// "Did you mean" for the bundled dictionary. Reads the word list only when it is first needed.
    private static let spellingSuggester = bundledDictionary.map { dictionary in
        SpellingSuggester(loadCandidates: { (try? dictionary.spellingCandidates()) ?? [] })
    }

    /// Word lookup: bundled dictionary, then local cache, then our backend. Throws if `BackendBaseURL` is missing.
    static func makeWordLookup() throws -> any WordLooking {
        WordLookup(
            dictionary: bundledDictionary,
            backend: BackendClient(baseURL: try AppConfig.backendBaseURL(), cache: ResultCache.shared),
            suggester: spellingSuggester)
    }

    /// DeepSeek, OpenAI-compatible. Models: https://api-docs.deepseek.com
    static func makeDeepSeekClient() throws -> any Translating {
        LLMClient(
            apiKey: try AppConfig.apiKey(for: .deepSeek),
            baseURL: URL(string: "https://api.deepseek.com")!,
            model: "deepseek-v4-flash")
    }

    /// OpenAI. Models: https://platform.openai.com/docs/models
    static func makeOpenAIClient() throws -> any Translating {
        LLMClient(
            apiKey: try AppConfig.apiKey(for: .openAI),
            baseURL: URL(string: "https://api.openai.com/v1")!,
            model: "gpt-4.1-mini")
    }

    /// Anthropic Claude. Models: https://docs.claude.com/en/docs/about-claude/models
    static func makeClaudeClient() throws -> any Translating {
        ClaudeClient(
            apiKey: try AppConfig.apiKey(for: .claude),
            model: "claude-haiku-5-5")
    }
}
