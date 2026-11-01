package org.humsaadhak.identity.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Password rules shared by the server and the set-password screen (frontend passwordRules.ts mirrors this):
 * at least 12 characters, not only letters, and not containing the email's name part.
 */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 12;

    private PasswordPolicy() {}

    public static List<String> problems(String password, String email) {
        List<String> p = new ArrayList<>();
        String pw = password == null ? "" : password;
        if (pw.length() < MIN_LENGTH) p.add("Use at least " + MIN_LENGTH + " characters.");
        if (pw.chars().allMatch(Character::isLetter)) p.add("Add a number or a symbol.");
        if (email != null && email.contains("@")) {
            String name = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
            if (name.length() >= 3 && pw.toLowerCase(Locale.ROOT).contains(name)) p.add("Do not use your email name.");
        }
        return p;
    }
}
