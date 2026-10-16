/** One explanation from start to end: word or sentence, then the events for the card. Used by the service worker. */
import { ApiError, MESSAGES, type SaynataApi } from "./api";
import type { ExplainEvent, ExplainRequest } from "./messages";
import { classify, MAX_SENTENCE_LENGTH } from "./textKind";

export async function explain(
  api: SaynataApi,
  request: ExplainRequest,
  emit: (event: ExplainEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const text = classify(request.text);
  try {
    switch (text.kind) {
      case "empty":
        emit({ type: "error", kind: "empty", message: "Select some English text first." });
        return;
      case "tooLong":
        emit({ type: "error", kind: "tooLong", message: `Select up to ${MAX_SENTENCE_LENGTH} characters.` });
        return;
      case "word":
        emit({ type: "word", response: await api.lookUpWord(text.word, signal) });
        return;
      case "sentence": {
        const response = await api.explainSentence(
          text.text,
          request.targetLang,
          {
            onMeaningDelta: (delta) => emit({ type: "meaningDelta", text: delta }),
            onMeaning: (meaning) => emit({ type: "meaning", meaning }),
          },
          signal,
        );
        emit({ type: "sentence", response });
        return;
      }
    }
  } catch (error) {
    if (signal?.aborted) return;
    if (error instanceof ApiError) emit({ type: "error", kind: error.kind, message: error.message });
    else emit({ type: "error", kind: "server", message: MESSAGES.generic });
  }
}
