package com.englishtranslation.backend.claude;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Keeps a sentence summary inside the small Markdown subset the clients render (prompt v9, TASK-89.3):
 * plain lines, bullet lines that start with "- " (at most {@value #MAX_BULLETS}), and **bold**.
 * A model sometimes writes other bullets ("* ", "• ", "1. "), headings or a lone "**"; they become the subset,
 * so the app, the Mac app and the browser extension never show stray markers.
 */
public final class SummaryFormat {

    static final int MAX_BULLETS = 3;

    private static final Pattern BULLET = Pattern.compile("^(?:[-*•]|\\d+[.)])\\s+");
    private static final Pattern HEADING = Pattern.compile("^#+\\s*");

    private SummaryFormat() {
    }

    public static String clean(String summary) {
        if (summary == null || summary.isBlank()) {
            return summary == null ? "" : summary.strip();
        }
        List<String> lines = new ArrayList<>();
        int bullets = 0;
        for (String raw : summary.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String line = HEADING.matcher(raw.strip()).replaceFirst("");
            if (line.isEmpty()) {
                continue;
            }
            var bullet = BULLET.matcher(line);
            if (bullet.find()) {
                if (bullets == MAX_BULLETS) {
                    continue;
                }
                bullets++;
                line = "- " + bullet.replaceFirst("").strip();
            }
            lines.add(balanceBold(line));
        }
        return String.join("\n", lines);
    }

    /** An odd number of "**" means one is not closed: then the line keeps no bold at all. */
    private static String balanceBold(String line) {
        int count = line.split("\\*\\*", -1).length - 1;
        return count % 2 == 0 ? line : line.replace("**", "");
    }
}
