package com.englishtranslation.backend.translation;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Turns Claude's streamed JSON text into two kinds of callbacks (TASK-73):
 * <ul>
 * <li>{@code onDelta}: the new characters of the meaning, as it grows. Sent at most once per
 * {@link #MIN_GAP_NANOS} so a fast stream does not make thousands of tiny events;</li>
 * <li>{@code onMeaning}: the full meaning, once, when its closing quote arrives. The characters that were still
 * waiting for the throttle are sent as a last delta just before it.</li>
 * </ul>
 * A blank meaning (the answer for text that is not English) sends nothing at all.
 * Feed the pieces in order with {@link #feed(String)}.
 */
class MeaningStream {

    /** About one delta every 50 ms. */
    static final long MIN_GAP_NANOS = 50_000_000L;

    private final JsonStringFieldReader reader = new JsonStringFieldReader("meaning");
    private final Consumer<String> onDelta;
    private final Consumer<String> onMeaning;
    private final LongSupplier nanoClock;
    private final StringBuilder waiting = new StringBuilder();
    private boolean sentAny = false;
    private long lastSentAt;

    MeaningStream(Consumer<String> onDelta, Consumer<String> onMeaning) {
        this(onDelta, onMeaning, System::nanoTime);
    }

    /** The clock can be replaced in a test. */
    MeaningStream(Consumer<String> onDelta, Consumer<String> onMeaning, LongSupplier nanoClock) {
        this.onDelta = onDelta;
        this.onMeaning = onMeaning;
        this.nanoClock = nanoClock;
    }

    void feed(String piece) {
        Optional<String> complete = reader.feed(piece);
        waiting.append(reader.drainDelta());

        if (complete.isPresent()) {
            if (!complete.get().isBlank()) {
                sendWaiting();
                onMeaning.accept(complete.get());
            }
            return;
        }
        long now = nanoClock.getAsLong();
        if (waiting.length() > 0 && (!sentAny || now - lastSentAt >= MIN_GAP_NANOS)) {
            sendWaiting();
        }
    }

    private void sendWaiting() {
        if (waiting.length() == 0) {
            return;
        }
        String text = waiting.toString();
        waiting.setLength(0);
        sentAny = true;
        lastSentAt = nanoClock.getAsLong();
        onDelta.accept(text);
    }
}
