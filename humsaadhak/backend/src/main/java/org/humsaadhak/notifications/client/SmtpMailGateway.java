package org.humsaadhak.notifications.client;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends email through an SMTP server, e.g. Microsoft 365 (smtp.office365.com, port 587, STARTTLS required). Host,
 * user and password come from environment variables only (S4); see spring.mail in application.yml. redirect-to sends
 * every mail to one inbox outside production, as with Graph.
 */
@Component
@ConditionalOnProperty(name = "humsaadhak.mail.provider", havingValue = "smtp")
public class SmtpMailGateway implements MailGateway {
    private static final Logger log = LoggerFactory.getLogger(SmtpMailGateway.class);
    private final JavaMailSender sender;
    private final String from, redirectTo, username;

    public SmtpMailGateway(JavaMailSender sender, @Value("${humsaadhak.mail.from}") String from,
                           @Value("${humsaadhak.mail.redirect-to:}") String redirectTo,
                           @Value("${humsaadhak.mail.smtp-ssl-trust:}") String sslTrust,
                           @Value("${spring.mail.username:}") String username,
                           @Value("${spring.mail.host:}") String host, @Value("${spring.mail.port:587}") String port) {
        this.sender = sender; this.from = from; this.redirectTo = redirectTo; this.username = username;
        // One line at start-up saying how email goes out, so a quiet log can be read
        log.info("Email: SMTP {}:{} as {} (STARTTLS required), from {}{}", host, port, username, from,
                redirectTo.isBlank() ? "" : "; every email goes to " + redirectTo + " instead of its recipients");
        // mail.smtp.ssl.trust only when SMTP_SSL_TRUST is set (e.g. smtp.office365.com): that host's certificate is then
        // accepted without verification. Left empty, the certificate is verified as usual.
        if (!sslTrust.isBlank() && sender instanceof JavaMailSenderImpl impl) {
            impl.getJavaMailProperties().setProperty("mail.smtp.ssl.trust", sslTrust.strip());
            log.warn("SMTP_SSL_TRUST={}: that mail server's certificate is not verified", sslTrust.strip());
        }
    }

    @Override
    public void send(Mail mail) {
        List<String> to = redirectTo.isBlank() ? mail.to() : List.of(redirectTo);
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper m = new MimeMessageHelper(message, false, "UTF-8");
            m.setFrom(from);
            m.setTo(to.toArray(String[]::new));
            m.setSubject(mail.subject());
            m.setText(mail.html(), true);
            sender.send(message);
            // The Message-ID finds this email in the mail server's records (Microsoft 365: Exchange admin centre, message trace)
            log.info("Email sent via SMTP to {}: \"{}\" (Message-ID {})", to, mail.subject(), message.getMessageID());
        } catch (MailAuthenticationException e) {
            // e.g. "535 5.7.3 Authentication unsuccessful": say what it means rather than only a stack trace
            throw new IllegalStateException("The mail server refused the sign-in for " + username + " (" + rootMessage(e) + "). Check "
                    + "SMTP_USERNAME and SMTP_PASSWORD; with Microsoft 365, Authenticated SMTP must be on for the mailbox and an account "
                    + "with multi-factor sign-in needs an app password. Test the settings on their own: deploy_local.cmd smtp-test", e);
        } catch (MessagingException e) {
            throw new IllegalStateException("The email could not be prepared: " + e.getMessage(), e);
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return String.valueOf(t.getMessage()).strip();
    }
}
