package com.englishtranslation.backend.auth;

import java.util.Locale;
import java.util.regex.Pattern;

/** Cleans and checks an email address. */
public final class Emails {

    /** Not a full RFC check on purpose: one @, no spaces, a dot in the domain. The code email is the real test. */
    private static final Pattern SHAPE = Pattern.compile("^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+$");
    private static final int MAX_LENGTH = 254;

    private Emails() {
    }

    /** Trims and lower-cases. Throws {@link InvalidEmailException} when it does not look like an email. */
    public static String normalize(String raw) {
        if (raw == null) {
            throw new InvalidEmailException();
        }
        String email = raw.trim().toLowerCase(Locale.ROOT);
        if (email.length() > MAX_LENGTH || !SHAPE.matcher(email).matches()) {
            throw new InvalidEmailException();
        }
        return email;
    }
}
