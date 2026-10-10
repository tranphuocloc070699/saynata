//
//  L3_Observable.swift
//  Lesson 3: @Observable (a store object instead of one value)
//
//  GOAL: move the counter into a class, so logic lives outside the view.
//  RULE: @Observable on a class = "SwiftUI watches the properties views read".
//        The view that CREATES the object uses @State to keep it alive.
//  React: this is a zustand store. The class is the store, `increment()` is an action.
//

import SwiftUI

@Observable
@MainActor
final class L3CounterStore {
    // TODO 1: add `var count = 0`
    var count = 0
    
    func increment(){
        count += 1
    }
    

    // TODO 2: add a method `func increment() { count += 1 }`
    //         Check your `///` doc comment rule from swift.md.
}

struct L3View: View {
    @State private var store = L3CounterStore()
    
    
    // TODO 3: add `@State private var store = L3CounterStore()`
    //         Why @State for a class? Without it, SwiftUI would create a NEW store
    //         every time `body` runs, and the count would reset to 0.

    var body: some View {
        VStack(spacing: 16) {
            // TODO 4: show `store.count` in a Text.
            Text("Count: \(store.count)")

            // TODO 5: make the button call `store.increment()`.
            Button("Add 1") {
                store.increment()
            }
        }
        .padding()
    }
}

#Preview {
    L3View()
}
