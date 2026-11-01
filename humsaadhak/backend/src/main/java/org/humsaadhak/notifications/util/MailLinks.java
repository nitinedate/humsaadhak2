package org.humsaadhak.notifications.util;

import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/** The links in an email body (pure, unit-tested): what a trial deployment logs so people can sign in without real email. */
public final class MailLinks {
    private static final Pattern LINK = Pattern.compile("https?://[^\"'<>\\s]+");

    private MailLinks() {}

    public static List<String> in(String html) {
        if (html == null) return List.of();
        return LINK.matcher(html).results().map(MatchResult::group).map(u -> u.replace("&amp;", "&")).distinct().toList();
    }
}
