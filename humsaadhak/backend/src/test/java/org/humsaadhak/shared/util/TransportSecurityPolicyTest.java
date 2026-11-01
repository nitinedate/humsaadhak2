package org.humsaadhak.shared.util;

import static org.assertj.core.api.Assertions.*;

import org.humsaadhak.shared.util.TransportSecurityPolicy.Settings;
import org.junit.jupiter.api.Test;

class TransportSecurityPolicyTest {
    static final Settings PRODUCTION = new Settings("https://humsaadhak.org",
            "jdbc:postgresql://db:5432/humsaadhak?sslmode=verify-full&sslrootcert=/tls/ca.crt",
            "https://search:8000", "https://storage:9000", "https://files.humsaadhak.org", true, true);

    @Test
    void aFullyEncryptedSetupPasses() {
        assertThat(TransportSecurityPolicy.problems(PRODUCTION)).isEmpty();
        // AWS S3 needs no endpoint: the SDK always uses https
        assertThat(TransportSecurityPolicy.problems(new Settings("https://humsaadhak.org", PRODUCTION.datasourceUrl(), "https://search:8000",
                "", null, true, true))).isEmpty();
        assertThat(TransportSecurityPolicy.problems(new Settings("https://humsaadhak.org", "jdbc:postgresql://db/h?SSLMODE=Verify-CA",
                "https://search:8000", null, null, true, true))).isEmpty();
    }

    @Test
    void everyUnencryptedHopIsReported() {
        var problems = TransportSecurityPolicy.problems(new Settings("http://localhost:8080", "jdbc:postgresql://db:5432/humsaadhak",
                "http://search:8000", "http://storage:9000", "http://storage:9000", false, false));
        assertThat(problems.size()).isEqualTo(7);
        assertThat(String.join("\n", problems).contains("found no sslmode")).isTrue();
    }

    @Test
    void sslmodesThatDoNotVerifyTheServerAreNotEnough() {
        for (String weak : new String[] { "require", "prefer", "allow", "disable" }) {
            var problems = TransportSecurityPolicy.problems(new Settings(PRODUCTION.appUrl(), "jdbc:postgresql://db/h?ssl=true&sslmode=" + weak,
                    PRODUCTION.searchUrl(), PRODUCTION.storageEndpoint(), PRODUCTION.storagePublicEndpoint(), true, true));
            assertThat(problems.size()).isEqualTo(1);
            assertThat(problems.get(0).contains("sslmode=" + weak)).isTrue();
        }
        assertThat(TransportSecurityPolicy.jdbcParam("jdbc:postgresql://db/h?a=1&sslmode=verify-full", "sslmode")).isEqualTo("verify-full");
        assertThat(TransportSecurityPolicy.jdbcParam("jdbc:postgresql://db/h", "sslmode")).isEqualTo(null);
    }

    @Test
    void emailOverSmtpMustBeEncryptedAndCheckTheServer() {
        assertThat(TransportSecurityPolicy.mailProblems("graph", false, false, false)).isEmpty();      // not SMTP
        assertThat(TransportSecurityPolicy.mailProblems("smtp", true, false, true)).isEmpty();          // STARTTLS on 587
        assertThat(TransportSecurityPolicy.mailProblems("smtp", false, true, true)).isEmpty();          // TLS on 465
        assertThat(TransportSecurityPolicy.mailProblems("smtp", false, false, true).size()).isEqualTo(1);
        assertThat(TransportSecurityPolicy.mailProblems("smtp", true, false, false).size()).isEqualTo(1);
    }

    @Test
    void trustingAnSmtpHostIsAllowedButWarnedAbout() {
        assertThat(TransportSecurityPolicy.mailWarnings("smtp", "")).isEmpty();
        assertThat(TransportSecurityPolicy.mailWarnings("graph", "smtp.office365.com")).isEmpty();
        assertThat(TransportSecurityPolicy.mailWarnings("smtp", "smtp.office365.com").get(0).contains("not verified")).isTrue();
    }

    @Test
    void theSmtpSettingsInApplicationYmlPassTheCheck() throws Exception {
        // Read the way Spring Boot gives them to the mail sender: "[mail.smtp.starttls.required]": true -> mail.smtp.starttls.required
        java.util.Map<String, String> javaMail = new java.util.HashMap<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"\\[(mail\\.[^\\]]+)\\]\":\\s*(\\S+)")
                .matcher(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/application.yml")));
        while (m.find()) javaMail.put(m.group(1), m.group(2));
        assertThat(javaMail.get("mail.smtp.starttls.required")).isEqualTo("true");
        assertThat(TransportSecurityPolicy.mailProblems("smtp", javaMail)).isEmpty();
        assertThat(TransportSecurityPolicy.mailProblems("smtp", java.util.Map.of()).size()).isEqualTo(2);   // nothing set: refused
    }
}

