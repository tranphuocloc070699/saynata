package com.englishtranslation.backend.translation;

import java.util.Optional;

/**
 * Reads ONE top-level string field out of a JSON object that arrives in small pieces.
 *
 * Claude streams its answer a few characters at a time. We want the "meaning" text as soon as that
 * string is closed, long before the rest of the JSON has been written. Feed the pieces in order with
 * {@link #feed(String)}; it gives the value back once, the moment the closing quote arrives.
 *
 * This is not a full JSON parser. It only keeps track of what it needs: whether we are inside a string,
 * how deep we are in { } and [ ], and which top-level key was just read. Words inside the "words" list
 * also have a "meaning" key, but they are deeper than level 1, so they are ignored. The final answer is
 * still parsed by Jackson, as always.
 */
public class JsonStringFieldReader {

    private enum StringKind { NONE, KEY, WANTED_VALUE, OTHER }

    private final String fieldName;

    private int depth = 0;
    private boolean inString = false;
    private boolean escaping = false;
    /** Hex digits we still expect after a backslash-u. */
    private int unicodeDigitsLeft = 0;
    private int unicodeValue = 0;
    private StringKind stringKind = StringKind.NONE;
    private final StringBuilder current = new StringBuilder();

    /** The last top-level key we read, until the next comma. */
    private String lastKey = null;
    private boolean sawColon = false;
    private boolean finished = false;

    public JsonStringFieldReader(String fieldName) {
        this.fieldName = fieldName;
    }

    /** Adds the next piece of the JSON. Returns the field value once, when its string is complete. */
    public Optional<String> feed(String chunk) {
        for (int i = 0; i < chunk.length() && !finished; i++) {
            String value = readChar(chunk.charAt(i));
            if (value != null) {
                finished = true;
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /** Returns the finished value, or null while there is more to read. */
    private String readChar(char c) {
        return inString ? readStringChar(c) : readStructureChar(c);
    }

    private String readStringChar(char c) {
        if (unicodeDigitsLeft > 0) {
            unicodeValue = unicodeValue * 16 + Character.digit(c, 16);
            unicodeDigitsLeft--;
            if (unicodeDigitsLeft == 0) {
                current.append((char) unicodeValue);
            }
            return null;
        }
        if (escaping) {
            escaping = false;
            switch (c) {
                case 'n' -> current.append('\n');
                case 't' -> current.append('\t');
                case 'r' -> current.append('\r');
                case 'b' -> current.append('\b');
                case 'f' -> current.append('\f');
                case 'u' -> {
                    unicodeDigitsLeft = 4;
                    unicodeValue = 0;
                }
                default -> current.append(c); // \" \\ \/
            }
            return null;
        }
        if (c == '\\') {
            escaping = true;
            return null;
        }
        if (c != '"') {
            current.append(c);
            return null;
        }

        // The closing quote.
        inString = false;
        StringKind kind = stringKind;
        stringKind = StringKind.NONE;
        if (kind == StringKind.WANTED_VALUE) {
            return current.toString();
        }
        if (kind == StringKind.KEY) {
            lastKey = current.toString();
        }
        return null;
    }

    private String readStructureChar(char c) {
        switch (c) {
            case '"' -> {
                inString = true;
                current.setLength(0);
                stringKind = kindOfNewString();
            }
            case '{', '[' -> depth++;
            case '}', ']' -> depth--;
            case ':' -> sawColon = depth == 1 && lastKey != null;
            case ',' -> {
                if (depth == 1) {
                    lastKey = null;
                    sawColon = false;
                }
            }
            default -> {
                // Spaces, numbers, true, false, null: nothing to track.
            }
        }
        return null;
    }

    private StringKind kindOfNewString() {
        if (depth != 1) {
            return StringKind.OTHER;
        }
        if (lastKey == null) {
            return StringKind.KEY;
        }
        return sawColon && lastKey.equals(fieldName) ? StringKind.WANTED_VALUE : StringKind.OTHER;
    }
}
