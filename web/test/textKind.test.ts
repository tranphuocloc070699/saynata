import { describe, expect, it } from "vitest";
import { classify, cleanSelection, wordQuery } from "../src/lib/textKind";

describe("wordQuery", () => {
  it("takes one word, lowercased", () => {
    expect(wordQuery("Reluctant")).toBe("reluctant");
  });

  it("drops a/an/the before one word, like the app (TASK-44)", () => {
    expect(wordQuery("an apple")).toBe("apple");
    expect(wordQuery("The dog")).toBe("dog");
  });

  it("strips punctuation at the edges", () => {
    expect(wordQuery("apple.")).toBe("apple");
    expect(wordQuery("(apple),")).toBe("apple");
  });

  it("keeps ' and - inside a word", () => {
    expect(wordQuery("don't")).toBe("don't");
    expect(wordQuery("well-known")).toBe("well-known");
  });

  it("is null for two words, or a word with digits", () => {
    expect(wordQuery("big dog")).toBeNull();
    expect(wordQuery("a big dog")).toBeNull();
    expect(wordQuery("abc123")).toBeNull();
    expect(wordQuery("...")).toBeNull();
  });

  it("is null for a word longer than the API accepts", () => {
    expect(wordQuery("a".repeat(65))).toBeNull();
  });
});

describe("classify", () => {
  it("sends one word to the word API", () => {
    expect(classify("  Apple  ")).toEqual({ kind: "word", word: "apple" });
  });

  it("sends more than one word to the sentence API, cleaned", () => {
    expect(classify("reluctant to\n  approve")).toEqual({ kind: "sentence", text: "reluctant to approve" });
  });

  it("refuses empty text and text over 500 characters", () => {
    expect(classify("  \n ")).toEqual({ kind: "empty" });
    expect(classify("word ".repeat(120))).toEqual({ kind: "tooLong", length: 599 });
  });

  it("turns curly quotes into straight ones", () => {
    expect(cleanSelection("it’s “fine”")).toBe("it's \"fine\"");
    expect(classify("don’t")).toEqual({ kind: "word", word: "don't" });
  });
});
