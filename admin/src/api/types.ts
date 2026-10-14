// API shapes from the TASK-49.1 contract. When the backend lands and a name differs,
// change it here and the compiler will point to every place that uses it.

export type Cefr = "A1" | "A2" | "B1" | "B2" | "C1" | "C2";

export type ExampleLevel = "easy" | "medium" | "harder";

export interface Example {
  level: ExampleLevel;
  text: string;
}

export interface UsageLine {
  kind: string; // pattern, collocation, phrase, idiom
  pattern: string;
  example: string;
}

export interface Sense {
  pos: string; // part of speech
  cefr: Cefr;
  simpleDefinition: string; // simple English explanation
  vietnamese: string;
  examples: Example[];
  usage: UsageLine[];
}

// The picture or clip of one sense, with the Pixabay credit (TASK-49.4).
export interface SenseMedia {
  url: string;
  kind: "photo" | "clip";
  photographer: string;
  pageUrl: string;
  licenseNote?: string;
}

export interface WordEntry {
  senses: Sense[];
}

export interface WordItem {
  id: number | string;
  word: string;
  entry: WordEntry;
  ipa?: string;
  needsReview: boolean;
  source: "bundled" | "backend";
  model: string;
  promptVersion: string;
  createdAt: string;
  // One item per sense, same order as entry.senses; null when a sense has no media.
  media?: (SenseMedia | null)[];
}

export interface SentenceWord {
  word: string;
  partOfSpeech: string;
  meaning: string;
}

export interface SentenceExplanation {
  meaning: string; // simple English explanation
  words: SentenceWord[];
  translation: string; // target language, shown last
}

export interface SentenceItem {
  id: number | string;
  sourceText: string;
  targetLang: string;
  explanation: SentenceExplanation;
  needsReview?: boolean;
  model: string;
  promptVersion: string;
  createdAt: string;
}

export interface AdminUser {
  id: number | string;
  email: string;
  displayName: string;
  roles: string[];
}
