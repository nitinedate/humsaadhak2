package org.humsaadhak.matching.client;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import org.humsaadhak.shared.security.InternalTls;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Talks to the Python humsaadhak-search service. Results are keyed by entity id, never by list position (fixes D1).
 * Internal network only; authenticated with a shared service token.
 */
@Component
public class SearchClient {
    public record Card(String entityType, long entityId, String kind, String name, String description, String sector,
                String amount, String periodicity) {}
    public record Hit(long entityId, int rank, double retrievalScore, double rerankScore, int score, Map<String, Object> explanation) {}
    public record MatchResponse(String modelVersion, List<Hit> hits) {}
    public record MatchRequest(long queryId, String queryType, int topK, Map<String, Object> filters) {}
    public record Label(long purposeId, long needId, String label, String source) {}

    private final RestClient http;

    /** Calls the search service over TLS 1.3/1.2, verifying its certificate (internal CA) and hostname. */
    public SearchClient(@Value("${humsaadhak.search.url}") String baseUrl, @Value("${humsaadhak.search.token}") String token,
                 @Value("${humsaadhak.search.timeout-ms:2000}") int timeoutMs, InternalTls tls) {
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(tls.httpClient().connectTimeout(Duration.ofMillis(timeoutMs)).build());
        f.setReadTimeout(Duration.ofMillis(timeoutMs * 15L)); // batch matching may rerank many candidates
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(f)
                .defaultHeader("Authorization", "Bearer " + token).build();
    }

    public void upsert(Card card) {
        http.post().uri("/v1/index/upsert").body(card).retrieve().toBodilessEntity();
    }

    public void delete(String entityType, long id) {
        http.delete().uri("/v1/index/{type}/{id}", entityType, id).retrieve().toBodilessEntity();
    }

    public MatchResponse purposeToNeeds(long purposeId, int topK, Map<String, Object> filters) {
        return http.post().uri("/v1/match/purpose-to-needs").body(new MatchRequest(purposeId, "purpose", topK, filters))
                .retrieve().body(MatchResponse.class);
    }

    public MatchResponse needToPurposes(long needId, int topK) {
        return http.post().uri("/v1/match/need-to-purposes").body(new MatchRequest(needId, "need", topK, Map.of()))
                .retrieve().body(MatchResponse.class);
    }

    public void sendLabels(List<Label> labels) {
        http.post().uri("/v1/feedback").body(labels).retrieve().toBodilessEntity();
    }
}
