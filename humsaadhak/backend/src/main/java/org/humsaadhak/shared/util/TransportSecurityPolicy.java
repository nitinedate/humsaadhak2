package org.humsaadhak.shared.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Encryption in transit, as a checklist over the application's connection settings (pure, unit-tested). With
 * humsaadhak.security.require-tls=true the application refuses to start while anything on this list is unencrypted.
 */
public final class TransportSecurityPolicy {
    /** The connection settings that decide whether data travels encrypted. */
    public record Settings(String appUrl, String datasourceUrl, String searchUrl, String storageEndpoint, String storagePublicEndpoint,
                           boolean sessionCookieSecure, boolean serverTls) {}

    private TransportSecurityPolicy() {}

    public static List<String> problems(Settings s) {
        List<String> out = new ArrayList<>();
        if (!https(s.appUrl())) out.add("The public address (APP_URL) must start with https://.");
        if (!s.serverTls()) out.add("The application must serve HTTPS itself (activate the 'tls' profile with TLS_CERT_FILE and TLS_KEY_FILE).");
        if (!s.sessionCookieSecure()) out.add("Session cookies must be marked Secure (COOKIE_SECURE=true).");
        String sslmode = jdbcParam(s.datasourceUrl(), "sslmode");
        if (!"verify-full".equals(sslmode) && !"verify-ca".equals(sslmode))
            out.add("The database connection (DB_URL) must use sslmode=verify-full with sslrootcert set; found "
                    + (sslmode == null ? "no sslmode" : "sslmode=" + sslmode) + ".");
        if (!https(s.searchUrl())) out.add("The search service (SEARCH_URL) must be reached over https://.");
        if (!blankOrHttps(s.storageEndpoint())) out.add("File storage (STORAGE_ENDPOINT) must be reached over https://.");
        if (!blankOrHttps(s.storagePublicEndpoint())) out.add("Document download links (STORAGE_PUBLIC_ENDPOINT) must use https://.");
        return out;
    }

    /** A query parameter of a JDBC URL, lower case, or null. */
    static String jdbcParam(String jdbcUrl, String name) {
        if (jdbcUrl == null) return null;
        int q = jdbcUrl.indexOf('?');
        if (q < 0) return null;
        for (String pair : jdbcUrl.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equalsIgnoreCase(name)) return pair.substring(eq + 1).toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private static boolean https(String url) {
        if (url == null || url.isBlank()) return false;
        try { return "https".equalsIgnoreCase(URI.create(url.trim()).getScheme()); } catch (IllegalArgumentException e) { return false; }
    }

    private static boolean blankOrHttps(String url) { return url == null || url.isBlank() || https(url); }

    /**
     * Email over SMTP must be encrypted: STARTTLS required (port 587) or TLS from the start (port 465), with the server's
     * name checked against its certificate.
     */
    public static List<String> mailProblems(String provider, boolean starttlsRequired, boolean sslEnabled, boolean checkServerIdentity) {
        if (!"smtp".equalsIgnoreCase(provider)) return List.of();
        List<String> problems = new java.util.ArrayList<>();
        if (!starttlsRequired && !sslEnabled)
            problems.add("email (SMTP) could be sent unencrypted: mail.smtp.starttls.required or mail.smtp.ssl.enable must be true");
        if (!checkServerIdentity) problems.add("the SMTP server's name is not checked against its certificate (mail.smtp.ssl.checkserveridentity)");
        return problems;
    }

    /**
     * The same rules, read from the JavaMail properties as Spring Boot hands them to the mail sender: keys such as
     * "mail.smtp.starttls.required" (written "[mail.smtp.starttls.required]" in YAML).
     */
    public static List<String> mailProblems(String provider, java.util.Map<String, String> javaMail) {
        return mailProblems(provider, on(javaMail, "mail.smtp.starttls.required"), on(javaMail, "mail.smtp.ssl.enable"),
                on(javaMail, "mail.smtp.ssl.checkserveridentity"));
    }

    private static boolean on(java.util.Map<String, String> map, String key) {
        String value = map.get(key);
        return value != null && Boolean.parseBoolean(value.strip());
    }

    /**
     * Allowed by the operator's choice, but said out loud: mail.smtp.ssl.trust (SMTP_SSL_TRUST) accepts the listed SMTP
     * hosts' certificates without verifying them. The mail is still encrypted, but a forged certificate would be accepted.
     */
    public static List<String> mailWarnings(String provider, String sslTrust) {
        if (!"smtp".equalsIgnoreCase(provider) || sslTrust == null || sslTrust.isBlank()) return List.of();
        return List.of("SMTP_SSL_TRUST=" + sslTrust.strip() + ": the certificate of that mail server is not verified (encrypted, but a forged "
                + "certificate would be accepted). Leave SMTP_SSL_TRUST empty to verify it.");
    }
}
