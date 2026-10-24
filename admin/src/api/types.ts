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

// The picture or clip of one sense, with the Pixabay credit (TASK-49.4). A file the admin uploaded (TASK-79) has
// source "upload" and no photographer or page: its credit is "Uploaded by the admin". An AI image made by the pipeline
// (TASK-53) has source "ai" and its licenseNote names the model, for example "AI image (openai gpt-image-1-mini)".
export interface SenseMedia {
  url: string;
  kind: "photo" | "clip";
  photographer: string | null;
  pageUrl: string | null;
  licenseNote?: string;
  source?: "pixabay" | "upload" | "ai";
}

// Where one sense stands in the picture review (TASK-80). "wrong" = marked needs_ai; "approved" = the admin said
// "Picture looks right" for the current file. verdict and reason are Claude's newest check (media_check), read-only.
export interface MediaReview {
  state: "approved" | "to_review" | "wrong" | "none";
  verdict?: "good" | "wrong" | "unsure" | null;
  reason?: string | null;
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
  // One item per sense: "needs_ai" when the sense is marked for an AI image (TASK-52), "ai_generated" after the AI job
  // drew its picture, "ai_drawing" while a job is queued or drawing (TASK-91), else null.
  mediaStatus?: (string | null)[];
  // Picture review (TASK-80): counts for the list badges, and one item per sense (same order as entry.senses).
  wrongMediaCount?: number;
  mediaToReviewCount?: number;
  mediaReview?: MediaReview[];
  // Why the word needs review, worked out by the backend from the entry as it is now (TASK-55).
  // Lines like "sense 1: the definition uses hard words [abstain]". Empty when there is nothing to fix.
  reviewReasons?: string[];
}

// One Pixabay result in the "Pick another" panel (TASK-52). Send it back as the body of PUT .../media.
export interface MediaSearchResult {
  kind: "photo" | "clip";
  pixabayId: number;
  previewUrl: string;
  downloadUrl: string;
  photographer: string;
  pageUrl: string;
}

// The AI picture of a sense (TASK-91). The prompt is the inner image prompt; the server adds the Codex wrapper text.
export type AiPromptSource = "scene" | "template" | "claude";

export interface AiUsage {
  picturesToday: number;
  picturesPerDay: number;
  sceneUsdToday: number;
  sceneUsdPerDay: number;
}

export interface AiPrompt {
  prompt: string;
  source: AiPromptSource;
  // The pipeline prompt version of the saved scene (for example "ai-v1"), only for source "scene".
  sceneVersion?: string | null;
  usage: AiUsage;
}

// One background job. While status is queued or drawing the admin page asks the server every 3 seconds.
export interface AiJob {
  id: number;
  status: "queued" | "drawing" | "done" | "failed";
  error?: string | null;
  createdAt: string;
  finishedAt?: string | null;
}

export interface MediaSearchResponse {
  query: string; // the search words that were used
  results: MediaSearchResult[];
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

// One row of the prefill queue (TASK-100): a word or sentence the night job will answer. GET /prefill/items.
export interface PrefillQueueItem {
  id: number;
  kind: "word" | "sentence";
  text: string;
  targetLang: string;
  source: string; // sentence-words = a learner met it in a sentence
  seenCount: number;
  priority: number;
  status: "pending" | "done" | "skipped" | "failed";
  attempts: number;
  lastError?: string | null;
  createdAt: string;
  updatedAt: string;
}

// App accounts on the admin Users page (TASK-105). GET /users and GET /users/{id}.
export type SignInProvider = "google" | "apple";

export interface UserRow {
  id: number;
  email: string;
  displayName: string;
  createdAt: string;
  lastSeenAt?: string | null; // saved on sign-in and token refresh, at most once an hour
  blocked: boolean;
  admin: boolean;
  providers: SignInProvider[]; // empty = signs in with an email code only
  explanationsToday: number;
  explanationsTotal: number;
  costTotalUsd: number;
}

export interface UsersSummary {
  accounts: number;
  activeToday: number;
  explanationsToday: number;
  costTodayUsd: number;
  usageSince?: string | null; // per-user usage was not saved before this
}

export interface UsagePeriod {
  calls: number;
  costUsd: number;
}

export interface UserDetail {
  user: UserRow;
  blockedReason?: string | null;
  blockedAt?: string | null;
  today: UsagePeriod;
  last7Days: UsagePeriod;
  total: UsagePeriod;
  wordsTotal: number;
  sentencesTotal: number;
  days: { date: string; calls: number }[];
  identities: { provider: SignInProvider; email?: string | null; since: string }[];
  likedCount: number;
  devicesSignedIn: number;
}
