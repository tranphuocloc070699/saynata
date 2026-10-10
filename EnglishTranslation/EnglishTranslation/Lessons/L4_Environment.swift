//
//  L4_Environment.swift
//  Lesson 4: .environment and @Environment (share one store with many views)
//
//  GOAL: two sibling views use the SAME store. One shows it, one edits it.
//  RULE: the owner calls `.environment(store)`. Children read with
//        `@Environment(L4Store.self)`. No parameters are passed down.
//  React: .environment is <Provider value={store}>, @Environment is useContext.
//
//  WARNING: if a child reads @Environment but the parent forgot `.environment(...)`,
//  the app CRASHES: "No Observable object of type L4Store found".
//  Try it on purpose after you finish, so you recognize that message.
//

import SwiftUI

@Observable
@MainActor
final class L4Store {
    var name = ""
    var count = 0
}

struct L4RootView: View {
    // TODO 1: add `@State private var store = L4Store()`
    @State private var store = L4Store()
    var body: some View {
        VStack(spacing: 24) {
            L4ReaderView()
            L4EditorView()
        }
        .environment(store)
        // TODO 2: add `.environment(store)` on the line below the VStack's closing brace.
        .padding()
    }
}

struct L4ReaderView: View {
    // TODO 3: add `@Environment(L4Store.self) private var store`
    
    @Environment(L4Store.self) private var store

    var body: some View {
        // TODO 4: replace with Text("\(store.name) clicked \(store.count) times")
        Text("\(store.name) clicked \(store.count)")
    }
}

struct L4EditorView: View {
    // TODO 5: add `@Environment(L4Store.self) private var store`
    
    @Environment(L4Store.self) private var store

    var body: some View {
        // TODO 6: as the FIRST line of body, write `@Bindable var store = store`.
        //         Why? `@Environment` gives you the object, but `$store.name` needs
        //         @Bindable to make a Binding out of one property.
        
        @Bindable var store = store
        
        VStack(spacing: 12) {
            // TODO 7: replace with TextField("Name", text: $store.name)
            TextField("Name",text: $store.name)

            // TODO 8: make the button do `store.count += 1`.
            Button("Click") {
                store.count += 1
            }
        }
    }
}

#Preview {
    L4RootView()
}
