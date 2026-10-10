//
//  L1_State.swift
//  Lesson 1: @State
//
//  GOAL: a button that adds 1 to a number, and a switch that shows or hides text.
//  RULE: @State is "this view owns this value". When it changes, SwiftUI runs `body` again.
//  React: this is useState. `count` is the value, and the setter is just `count += 1`.
//
//  HOW TO TEST: open the Canvas (⌥⌘↩), click the "Live" play button, and tap.
//

import SwiftUI

struct L1StateView: View {
    // TODO 1: add a property `@State private var count = 0`
    // TODO 4: add a property `@State private var isOn = false`
    @State private var count = 0
    @State private var isOn = false

    var body: some View {
        VStack(spacing: 16) {
            // TODO 2: make this Text show the real count, using \(count) inside the string.
            Text("Count: \(count)")

            // TODO 3: fill the button's braces so it adds 1 to count.
            Button("Add 1") {
                self.count += 1
            }

            // TODO 5: replace this Text with `Toggle("Show message", isOn: $isOn)`.
            //         `$isOn` is a Binding: a two-way link (read AND write) to the @State.
            Toggle("Show message", isOn: $isOn)

            // TODO 6: show the next line only when isOn is true, with `if isOn { ... }`.
            if isOn {
                Text("Hello! The switch is on.")
            }
        }
        .padding()
    }
}

#Preview {
    L1StateView()
}
