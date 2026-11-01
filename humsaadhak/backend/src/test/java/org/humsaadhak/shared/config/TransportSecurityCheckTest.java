package org.humsaadhak.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The start-up guard as Spring wires it (no database or Docker needed). */
class TransportSecurityCheckTest {
    final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(TransportSecurityCheck.class);

    static final String[] ENCRYPTED = {
        "humsaadhak.security.require-tls=true", "humsaadhak.app-url=https://humsaadhak.org",
        "spring.datasource.url=jdbc:postgresql://db:5432/humsaadhak?sslmode=verify-full&sslrootcert=/tls/app/ca.crt",
        "humsaadhak.search.url=https://search:8000", "humsaadhak.storage.endpoint=https://storage:9000",
        "humsaadhak.storage.public-endpoint=https://files.humsaadhak.org", "server.servlet.session.cookie.secure=true", "server.ssl.bundle=server" };

    @Test
    void productionRefusesToStartWithAnyPlainConnection() {
        runner.withPropertyValues(ENCRYPTED).withPropertyValues("humsaadhak.search.url=http://search:8000", "server.ssl.bundle=")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("Encryption in transit is required")
                            .hasMessageContaining("SEARCH_URL").hasMessageContaining("serve HTTPS itself");
                });
    }

    @Test
    void productionStartsWhenEverythingIsEncrypted() {
        runner.withPropertyValues(ENCRYPTED).run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(TransportSecurityCheck.class));
    }

    @Test
    void notCheckedWhereTlsIsNotRequired() {
        runner.withPropertyValues("humsaadhak.app-url=http://localhost:8080").run(ctx -> assertThat(ctx).hasNotFailed().doesNotHaveBean(TransportSecurityCheck.class));
    }

    @Test
    void smtpSettingsWrittenWithBracketsAsInApplicationYmlAreRead() {
        // The JavaMail keys are bracketed, as in application.yml; reading them with dots once refused a correct setup
        runner.withPropertyValues(ENCRYPTED).withPropertyValues("humsaadhak.mail.provider=smtp",
                        "spring.mail.properties[mail.smtp.starttls.required]=true", "spring.mail.properties[mail.smtp.ssl.checkserveridentity]=true")
                .run(ctx -> assertThat(ctx).hasNotFailed());
        runner.withPropertyValues(ENCRYPTED).withPropertyValues("humsaadhak.mail.provider=smtp")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}

