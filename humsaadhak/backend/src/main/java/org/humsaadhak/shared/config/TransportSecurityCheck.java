package org.humsaadhak.shared.config;

import java.util.List;
import java.util.Map;
import org.humsaadhak.shared.util.TransportSecurityPolicy;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * With humsaadhak.security.require-tls=true (production), the application refuses to start if any connection would
 * carry data unencrypted: browser to app, app to database, search service, file storage or mail server (SMTP).
 */
@Component
@ConditionalOnProperty(name = "humsaadhak.security.require-tls", havingValue = "true")
public class TransportSecurityCheck implements InitializingBean {
    private final Environment env;

    public TransportSecurityCheck(Environment env) { this.env = env; }

    @Override
    public void afterPropertiesSet() {
        boolean serverTls = !env.getProperty("server.ssl.bundle", "").isBlank() || env.getProperty("server.ssl.enabled", Boolean.class, false);
        List<String> problems = TransportSecurityPolicy.problems(new TransportSecurityPolicy.Settings(
                env.getProperty("humsaadhak.app-url"), env.getProperty("spring.datasource.url"), env.getProperty("humsaadhak.search.url"),
                env.getProperty("humsaadhak.storage.endpoint"), env.getProperty("humsaadhak.storage.public-endpoint"),
                env.getProperty("server.servlet.session.cookie.secure", Boolean.class, false), serverTls));
        problems = new java.util.ArrayList<>(problems);
        // The JavaMail properties exactly as Spring Boot gives them to the mail sender. They are written with brackets in
        // YAML ("[mail.smtp.starttls.required]"), so a getProperty with dots ("...properties.mail.smtp...") finds nothing.
        Map<String, String> javaMail = Binder.get(env)
                .bind("spring.mail.properties", Bindable.mapOf(String.class, String.class)).orElseGet(Map::of);
        String provider = env.getProperty("humsaadhak.mail.provider", "log");
        problems.addAll(TransportSecurityPolicy.mailProblems(provider, javaMail));
        TransportSecurityPolicy.mailWarnings(provider, env.getProperty("humsaadhak.mail.smtp-ssl-trust", ""))
                .forEach(w -> org.slf4j.LoggerFactory.getLogger(TransportSecurityCheck.class).warn(w));
        if (!problems.isEmpty())
            throw new IllegalStateException("Encryption in transit is required (REQUIRE_TLS=true), but:\n - " + String.join("\n - ", problems));
    }
}
