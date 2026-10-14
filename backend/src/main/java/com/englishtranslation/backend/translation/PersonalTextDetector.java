package com.englishtranslation.backend.translation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Decides if a sentence looks personal (email, phone number, link, long number).
 *
 * The sentence cache is shared by all users, so personal text must never be saved in it.
 * We lean towards "personal" when unsure: a wrong guess only means one extra Claude call.
 */
@Component
public class PersonalTextDetector {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");

    private static final Pattern LINK = Pattern.compile(
            "https?://|www\\.|\\b[a-z0-9-]+\\.(com|vn|net|org|io|edu|gov|co|app|dev|info)\\b",
            Pattern.CASE_INSENSITIVE);

    // Six or more digits in a row, like an ID or a card number.
    private static final Pattern LONG_NUMBER = Pattern.compile("\\d{6,}");

    // A group of digits that may have spaces, dots, dashes or parentheses inside, and a leading +.
    private static final Pattern DIGIT_GROUP = Pattern.compile("\\+?\\(?\\d[\\d\\s.\\-()]*\\d");

    private static final int PHONE_MIN_DIGITS = 7;

    public boolean looksPersonal(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return EMAIL.matcher(text).find()
                || LINK.matcher(text).find()
                || LONG_NUMBER.matcher(text).find()
                || hasPhoneNumber(text);
    }

    /** Seven or more digits in one group, even when spaces, dots, dashes or brackets split them. */
    private boolean hasPhoneNumber(String text) {
        Matcher matcher = DIGIT_GROUP.matcher(text);
        while (matcher.find()) {
            if (countDigits(matcher.group()) >= PHONE_MIN_DIGITS) {
                return true;
            }
        }
        return false;
    }

    private int countDigits(String group) {
        int count = 0;
        for (char c : group.toCharArray()) {
            if (Character.isDigit(c)) {
                count++;
            }
        }
        return count;
    }
}
