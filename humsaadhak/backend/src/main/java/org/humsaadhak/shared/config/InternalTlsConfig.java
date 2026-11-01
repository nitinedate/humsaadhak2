package org.humsaadhak.shared.config;

import java.nio.file.Path;
import org.humsaadhak.shared.security.InternalTls;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The trust used by clients of HumSaadhak's internal services (TLS_CA_FILE = the internal CA certificate). */
@Configuration
public class InternalTlsConfig {
    @Bean
    public InternalTls internalTls(@Value("${humsaadhak.tls.trust-ca:}") String caFile) {
        return caFile.isBlank() ? InternalTls.publicCasOnly() : InternalTls.trusting(Path.of(caFile));
    }
}
