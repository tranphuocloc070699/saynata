# Lesson answers

Try each lesson yourself first. Look here only after you are stuck for 10 minutes,
or to compare. This folder is outside the app target, so Xcode does not compile it.

## L1: @State

```swift
@State private var count = 0
@State private var isOn = false
// ...
Text("Count: \(count)")
Button("Add 1") { count += 1 }
Toggle("Show message", isOn: $isOn)
if isOn { Text("Hello! The switch is on.") }
```

## L2: @Binding

```swift
// parent
@State private var name = ""
L2NameField(text: $name)
Text("Hello, \(name)")

// child
@Binding var text: String
TextField("Your name", text: $text).textFieldStyle(.roundedBorder)
```

## L3: @Observable

```swift
@Observable @MainActor
final class L3CounterStore {
    var count = 0
    /// Adds 1 to the counter.
    func increment() { count += 1 }
}

@State private var store = L3CounterStore()
Text("Count: \(store.count)")
Button("Add 1") { store.increment() }
```

## L4: environment

```swift
// root
@State private var store = L4Store()
VStack { ... }.environment(store)

// reader
@Environment(L4Store.self) private var store
Text("\(store.name) clicked \(store.count) times")

// editor
@Environment(L4Store.self) private var store
var body: some View {
    @Bindable var store = store
    VStack {
        TextField("Name", text: $store.name)
        Button("Click") { store.count += 1 }
    }
}
```

## L5: async state

```swift
func run() async {
    state = .loading
    try? await Task.sleep(for: .milliseconds(1500))
    if shouldFail { state = .failed("Something broke") }
    else { state = .loaded("Done") }
}

switch store.state {
case .idle: Text("Press Run")
case .loading: ProgressView()
case let .loaded(text): Text(text)
case let .failed(message): Text(message).foregroundStyle(.red)
}

Button("Run") { Task { await store.run() } }
    .disabled({ if case .loading = store.state { true } else { false } }())
```

A cleaner way for the last line: add `var isLoading: Bool` to `L5Store`, then use
`.disabled(store.isLoading)`.

## L6 — closures and `@escaping`

1. `init` stores the closure in a property, so it outlives the call. Fix: `init(makeGreeting: @escaping (String) -> String)`.
2. `message = makeGreeting(name)`
3. `func runLater(_ work: @escaping () -> Void)` — `work()` runs inside a `Task`, after `runLater` returned.
4. `L6Store { name in "Hello, \(name)!" }` (single expression, no `return` needed).
5. `store.runLater { store.message = "Bye!" }` — no `self.` needed: `store` is a property of a struct view, not a class capture. `self.` is only required for class `self` inside escaping closures (and not at all in Swift 5.8+ for many cases with `[self]`).
6. The closure builds the client on each request. A missing key throws inside `translate()` and becomes `.failed(...)`. A plain value would need the key at launch and crash or need a fallback.
