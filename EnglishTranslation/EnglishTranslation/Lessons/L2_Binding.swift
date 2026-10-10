//
//  L2_Binding.swift
//  Lesson 2: @Binding (a child edits its parent's @State)
//
//  GOAL: a parent view owns a name. A child view has the text field and edits it.
//  RULE: @State = "I own it". @Binding = "my parent owns it, and I may edit it."
//  React: a Binding is `value` + `onChange` packed into one object.
//  You pass it with `$`: `L2NameField(text: $name)`.
//

import SwiftUI

struct L2ParentView: View {
    // TODO 1: add `@State private var name = ""`
    @State private var name = ""
    
    

    var body: some View {
        VStack(spacing: 16) {
            // TODO 2: change `""` to `$name` so the child gets a Binding, not a copy.
            L2NameField(text: $name)

            // TODO 3: show the name here: Text("Hello, \(name)")
            Text("Hello, \(name)")
        }
        .padding()
    }
}

struct L2NameField: View {
    // TODO 4: change this line to `@Binding var text: String`
    //         (then fix the call site in TODO 2 if Xcode complains).
    
    @Binding var text : String


    var body: some View {
        // TODO 5: replace the Text with `TextField("Your name", text: $text)`
        //         and add `.textFieldStyle(.roundedBorder)` below it.
        
        TextField("Your name", text: $text)
            .textFieldStyle(.roundedBorder)
        
    }
}

#Preview {
    L2ParentView()
}
