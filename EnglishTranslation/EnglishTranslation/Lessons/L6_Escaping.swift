//
//  L6_Escaping.swift
//  Lesson 6: closures and `@escaping` (this is the `makeTranslator` in TranslationStore)
//
//  THEORY
//   1. A closure is a function without a name that you can pass around or store.
//        let double: (Int) -> Int = { number in number * 2 }
//      The type `(Int) -> Int` means: takes an Int, returns an Int.
//      `() throws -> String` means: takes nothing, can throw, returns a String.
//
//   2. A closure parameter is NON-escaping by default. Swift promises that the
//      closure is called before the function returns, and then forgotten.
//        func run(_ work: () -> Void) { work() }   // fine
//
//   3. `@escaping` means: the closure may live LONGER than the function call.
//      That happens when you
//        - store it in a property (`self.makeTranslator = makeTranslator`), or
//        - call it later (after a delay, after a network call, in a Task).
//      Swift forces you to write `@escaping` so you know the closure can keep
//      things (like `self`) alive.
//
//   4. Rule of thumb: if the closure is stored or called later, add `@escaping`.
//      If the compiler says "Escaping closure captured by non-escaping parameter"
//      or "Assigning non-escaping parameter ... to an @escaping closure", add it.
//
//  GOAL: a small store that takes a "greeting maker" closure in its init.
//        Same idea as `TranslationStore(makeTranslator:)`.
//

import SwiftUI

@Observable
@MainActor
final class L6Store {
    var message = "Nothing yet"

    /// Stored for later, so it outlives `init`.
    private let makeGreeting: (String) -> String

    // TODO 1: this init does not compile. Read the error, then fix it.
    //   Why does Swift complain? (Answer in one sentence, in a comment below.)
    init(makeGreeting: @escaping (String) -> String) {
        self.makeGreeting = makeGreeting
    }

    /// Calls the stored closure, then updates `message`.
    func greet(_ name: String) {
        // TODO 2: set `message` by calling `makeGreeting` with `name`.
        self.message = self.makeGreeting(name)
        
        
    }

    /// Waits one second, then runs `work`. `work` is called AFTER this function returns.
    // TODO 3: this does not compile either. Add the one missing keyword to `work`.
    //   Hint: look at when `work()` runs compared to when `runLater` returns.
    func runLater(_ work: @escaping () -> Void) {
        Task {
            try? await Task.sleep(for: .seconds(1))
            work()
        }
    }
}

struct L6View: View {
    // TODO 4: build the store with a closure: given a name, return "Hello, <name>!".
    //   Try 1: trailing closure style      L6Store { name in ... }
    //   Try 2: a closure variable passed as `makeGreeting:`
    
    
    
    @State private var store = L6Store { name in
        "Hello, \(name)"
    }

    var body: some View {
        VStack(spacing: 16) {
            Text(store.message)
            Button("Greet Loc") {
                store.greet("Loc")
            }
            Button("Say bye in 1 second") {
                // TODO 5: call `store.runLater` with a closure that sets
                //   `store.message = "Bye!"`. This is a trailing closure.
                //   Think: does the closure need `self.`? Why or why not?
                
                store.runLater {
                    store.message = "Bye"
                }
                
            }
        }
        .padding()
    }
}

// TODO 6 (think, then answer in a comment):
//   In TranslationStore, `makeTranslator` is `() throws -> any Translating`.
//   Why is it a closure and not just a `LLMClient` value?
//   Hint: what happens at launch if the API key is missing?

#Preview {
    L6View()
}
