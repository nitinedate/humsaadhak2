package org.humsaadhak.notifications.client;

import org.humsaadhak.notifications.util.MailLinks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development and trial default: logs recipients and subject only. Bodies hold one-time links, so they are never logged,
 * unless MAIL_LOG_LINKS=true: then the links are logged too, so a trial on localhost can be signed into without email
 * (deploy/deploy.sh admin-link reads them). Never enable that where real people have accounts.
 */
@Component
@ConditionalOnProperty(name = "humsaadhak.mail.provider", havingValue = "log", matchIfMissing = true)
public class LoggingMailGateway implements MailGateway {
    private static final Logger log = LoggerFactory.getLogger(LoggingMailGateway.class);
    private final boolean logLinks;

    public LoggingMailGateway(@Value("${humsaadhak.mail.log-links:false}") boolean logLinks) { this.logLinks = logLinks; }

    @Override
    public void send(Mail mail) {
        log.info("Mail to {} — {}", mail.to(), mail.subject());
        if (logLinks) MailLinks.in(mail.html()).forEach(link -> log.info("Mail link for {}: {}", mail.to(), link));
    }
}
