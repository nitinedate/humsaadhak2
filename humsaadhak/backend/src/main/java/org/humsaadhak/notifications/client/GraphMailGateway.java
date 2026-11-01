package org.humsaadhak.notifications.client;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Sends through Microsoft Graph (the tenant the current system already uses) with the client-credentials flow.
 * Credentials come from environment variables only (S4). redirect-to sends every mail to one inbox outside production.
 */
@Component
@ConditionalOnProperty(name = "humsaadhak.mail.provider", havingValue = "graph")
public class GraphMailGateway implements MailGateway {
    private static final Logger log = LoggerFactory.getLogger(GraphMailGateway.class);
    private final RestClient http = RestClient.create();
    private final String tenant, clientId, clientSecret, from, redirectTo;
    private String token;
    private Instant tokenExpiry = Instant.EPOCH;

    public GraphMailGateway(@Value("${humsaadhak.mail.graph.tenant-id}") String tenant,
                     @Value("${humsaadhak.mail.graph.client-id}") String clientId,
                     @Value("${humsaadhak.mail.graph.client-secret}") String clientSecret,
                     @Value("${humsaadhak.mail.from}") String from,
                     @Value("${humsaadhak.mail.redirect-to:}") String redirectTo) {
        this.tenant = tenant; this.clientId = clientId; this.clientSecret = clientSecret; this.from = from; this.redirectTo = redirectTo;
        log.info("Email: Microsoft Graph, from {}{}", from,
                redirectTo.isBlank() ? "" : "; every email goes to " + redirectTo + " instead of its recipients");
    }

    @Override
    public void send(Mail mail) {
        List<String> to = redirectTo.isBlank() ? mail.to() : List.of(redirectTo);
        Map<String, Object> body = Map.of("message", Map.of(
                "subject", mail.subject(),
                "body", Map.of("contentType", "HTML", "content", mail.html()),
                "toRecipients", to.stream().map(a -> Map.of("emailAddress", Map.of("address", a))).toList()),
                "saveToSentItems", false);
        http.post().uri("https://graph.microsoft.com/v1.0/users/{from}/sendMail", from)
                .header("Authorization", "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
        log.info("Email accepted by Microsoft Graph for {}: \"{}\"", to, mail.subject());
    }

    @SuppressWarnings("unchecked")
    private synchronized String accessToken() {
        if (token != null && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) return token;
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("scope", "https://graph.microsoft.com/.default");
        form.add("grant_type", "client_credentials");
        Map<String, Object> res = http.post().uri("https://login.microsoftonline.com/{t}/oauth2/v2.0/token", tenant)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
        token = (String) res.get("access_token");
        tokenExpiry = Instant.now().plusSeconds(((Number) res.get("expires_in")).longValue());
        return token;
    }
}
