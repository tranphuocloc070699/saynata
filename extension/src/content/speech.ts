/** Reads English aloud with the Web Speech API (speechSynthesis), on the device; no backend. */
export class Speech {
  private current: SpeechSynthesisUtterance | null = null;

  constructor(private readonly onChange: (speaking: boolean) => void) {}

  get isSpeaking(): boolean {
    return this.current !== null;
  }

  /** Starts reading `text`, or stops when it is already reading. */
  toggle(text: string): void {
    if (this.current) {
      this.stop();
      return;
    }
    if (!("speechSynthesis" in window)) return;
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = "en-US";
    utterance.rate = 0.95;
    const voice = englishVoice();
    if (voice) utterance.voice = voice;
    const finished = () => {
      if (this.current !== utterance) return;
      this.current = null;
      this.onChange(false);
    };
    utterance.onend = finished;
    utterance.onerror = finished;
    speechSynthesis.cancel();
    this.current = utterance;
    speechSynthesis.speak(utterance);
    this.onChange(true);
  }

  stop(): void {
    if (!this.current) return;
    this.current = null;
    speechSynthesis.cancel();
    this.onChange(false);
  }
}

/** A local US English voice if there is one, else any English voice. */
function englishVoice(): SpeechSynthesisVoice | undefined {
  const voices = speechSynthesis.getVoices().filter((voice) => voice.lang.toLowerCase().startsWith("en"));
  return (
    voices.find((voice) => voice.lang === "en-US" && voice.localService) ??
    voices.find((voice) => voice.lang === "en-US") ??
    voices[0]
  );
}
