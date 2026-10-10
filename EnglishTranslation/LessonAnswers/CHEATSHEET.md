# Swift / SwiftUI cheat sheet (for Loc)

## Closures
```swift
let greet: (String) -> String = { name in "Hello, \(name)!" }   // JS: name => `Hello, ${name}!`
```
- Type `(A) -> B`: takes A, returns B. `() throws -> B`: no input, can throw.
- Last argument is a closure → **trailing closure**: `f(x) { ... }` instead of `f(x, action: { ... })`.
- `\(value)` inside a string = `${value}` in JS.

## @escaping
- Closure is **stored** or **called later** → write `@escaping`.
- Otherwise it must finish before the function returns (default).
- `init(make: @escaping () -> X) { self.make = make }`  ← stored, so escaping.

## async / await / Task
- `await` suspends, never blocks the thread (same as JS).
- `await` only works inside an `async` function or a `Task { }`.
- Button action is not async → `Button("Go") { Task { await store.run() } }`.
- `Task { }` inside a `@MainActor` type stays on the main thread. It is NOT a new Java-style thread.
- `Task { }` = start and do not wait (like calling an async fn without `await` in JS).

## State, who owns what
| Wrapper | Meaning |
|---|---|
| `@State` | this view owns the value |
| `@Binding` | borrowed from a parent (`$value`) |
| `@Observable` class | shared model; views redraw only for properties they read |
| `.environment(store)` + `@Environment(Type.self)` | pass the store down without parameters |
| `@Bindable var store = store` | needed to write `$store.property` |

## Enum state instead of booleans
`enum State { idle, loading, loaded(String), failed(String) }`
- Check one case: `if case .loading = state { ... }` (a computed `isLoading`).
- `==` works only if the enum is `Equatable`.

## try
- `try` → can throw, handle with `do/catch`
- `try?` → ignore the error, result becomes `nil`
- `try!` → crash on error (avoid)

## Self-test (answer without looking)
1. Why does `init(make: () -> X)` fail when you store `make`?
2. Why does a Button need `Task { }` to call an async function?
3. What is the difference between `Task { }` and `Task.detached { }`?
4. Why is `makeTranslator` a closure and not a plain `LLMClient`?
