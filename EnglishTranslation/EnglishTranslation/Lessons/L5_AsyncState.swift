//
//  L5_AsyncState.swift
//  Lesson 5: async work + an enum state (this is what TranslationStore does)
//
//  GOAL: a "Run" button waits 1.5 seconds, then shows "Done", or an error if the
//        switch "Make it fail" is on. No network, so you can focus on the pattern.
//  RULES:
//   - One enum for the whole screen state, instead of several booleans.
//   - `async` functions are called with `await`, from a `Task` when you are in a button.
//   - The view only READS `state`. Only the store WRITES it.
//

import SwiftUI

enum L5State {
    case idle
    case loading
    case loaded(String)
    case failed(String)
}

@Observable
@MainActor
final class L5Store {
    var state: L5State = .idle
    var shouldFail = false
    
    /// True while a request is running.
    var isLoading: Bool {
        if case .loading = state { return true }
        return false
    }

    /// Pretends to work, then sets `state`.
    func run() async {
        // TODO 1: write these steps, in order:
        //   a) state = .loading
        //   b) try? await Task.sleep(for: .milliseconds(1500))
        //   c) if shouldFail { state = .failed("Something broke") }
        //      else { state = .loaded("Done") }
        
        state = .loading
        try? await Task.sleep(for: .milliseconds(1500))
        if shouldFail { state = .failed("Something broke")}
        else {state = .loaded("Done")}
    }
}

struct L5View: View {
    @State private var store = L5Store()

    var body: some View {
        @Bindable var store = store

        VStack(spacing: 16) {
            // TODO 2: replace this Text with a `switch store.state { ... }`
            //         with one case for each of the four states:
            //           .idle      -> Text("Press Run")
            //           .loading   -> ProgressView()
            //           .loaded(let text)  -> Text(text)
            //           .failed(let message) -> Text(message).foregroundStyle(.red)
            //         Delete one case on purpose and read the compiler error.
            
            switch store.state {
            case .idle:
                Text ("Press run")
            case .loading:
                ProgressView()
            case .loaded(let string):
                Text(string).foregroundStyle(.green)
            case .failed(let string):
                Text(string).foregroundStyle(.red)
            }

            Toggle("Make it fail", isOn: $store.shouldFail)

            // TODO 3: make the button run `Task { await store.run() }`
            // TODO 4: add `.disabled(...)` so you cannot press it while loading.
            //         Hint: you need an `if case .loading = store.state` check, as in
            //         TranslationStore.canTranslate.
            Button("Run") {
                Task {
                    await store.run()
                    
                }
            }.disabled(store.isLoading)
        }
        .padding()
    }
}

#Preview {
    L5View()
}
