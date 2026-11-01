package org.humsaadhak.notifications.client;

import java.util.List;

/** Email addresses for the log: enough to see where mail went (n***@gmail.com), without writing the full address. */
public final class MailAddresses {
    private MailAddresses() {}

    public static String mask(String address) {
        if (address == null) return "***";
        String a = address.strip();
        int at = a.indexOf('@');
        if (at < 1 || at == a.length() - 1) return "***";
        return a.charAt(0) + "***" + a.substring(at);
    }

    public static String maskAll(List<String> addresses) {
        return addresses.stream().map(MailAddresses::mask).toList().toString();
    }
}
