package org.humsaadhak.shared.util;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.X509TrustManager;
import org.humsaadhak.shared.security.InternalTls;
import org.junit.jupiter.api.Test;

/**
 * Real TLS connections against a local HTTPS server using the test certificates in src/test/resources/tls
 * (made by deploy/tls/make-certs.sh): what is trusted, what is refused, and that only TLS 1.2/1.3 are spoken.
 */
class TlsSupportTest {
    static final Path DIR = Path.of("src/test/resources/tls");

    /** Starts an HTTPS server with the given certificate on a free port and returns it. */
    static HttpsServer server(String cert, String key) throws Exception {
        HttpsServer s = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        SSLContext ctx = TlsSupport.serverContext(DIR.resolve(cert), DIR.resolve(key));
        s.setHttpsConfigurator(new HttpsConfigurator(ctx) {
            @Override public void configure(HttpsParameters p) {
                SSLParameters sp = ctx.getDefaultSSLParameters();
                sp.setProtocols(TlsSupport.PROTOCOLS);
                p.setSSLParameters(sp);
            }
        });
        s.createContext("/ok", ex -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        s.start();
        return s;
    }

    static String get(HttpClient client, String host, int port) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("https://" + host + ":" + port + "/ok")).timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    @Test
    void aClientTrustingTheInternalCaConnectsAndOthersAreRefused() throws Exception {
        HttpsServer s = server("server.crt", "server.key");
        int port = s.getAddress().getPort();
        try {
            InternalTls internal = InternalTls.trusting(DIR.resolve("ca.crt"));
            assertThat(get(internal.httpClient().build(), "localhost", port)).isEqualTo("ok");

            // the JDK's public CAs alone do not know the internal CA
            assertThatThrownBy(() -> get(InternalTls.publicCasOnly().httpClient().build(), "localhost", port))
                    .isInstanceOf(javax.net.ssl.SSLHandshakeException.class);

            // the certificate names "testserver" and "localhost", not 127.0.0.1: the hostname check refuses it
            assertThatThrownBy(() -> get(internal.httpClient().build(), "127.0.0.1", port)).isInstanceOf(java.io.IOException.class);
        } finally {
            s.stop(0);
        }
    }

    @Test
    void aCertificateFromAnotherCaIsRefused() throws Exception {
        HttpsServer rogue = server("rogue-server.crt", "rogue-server.key");
        try {
            InternalTls internal = InternalTls.trusting(DIR.resolve("ca.crt"));
            assertThatThrownBy(() -> get(internal.httpClient().build(), "localhost", rogue.getAddress().getPort()))
                    .isInstanceOf(javax.net.ssl.SSLHandshakeException.class);
        } finally {
            rogue.stop(0);
        }
    }

    @Test
    void oldProtocolsAreRefused() throws Exception {
        HttpsServer s = server("server.crt", "server.key");
        try {
            SSLParameters tls11 = TlsSupport.clientParameters();
            tls11.setProtocols(new String[] { "TLSv1.1" });
            HttpClient old = HttpClient.newBuilder().sslContext(InternalTls.trusting(DIR.resolve("ca.crt")).sslContext()).sslParameters(tls11).build();
            assertThatThrownBy(() -> get(old, "localhost", s.getAddress().getPort())).isInstanceOf(java.io.IOException.class);
        } finally {
            s.stop(0);
        }
        assertThat(String.join(",", TlsSupport.clientParameters().getProtocols())).isEqualTo("TLSv1.3,TLSv1.2");
        assertThat(TlsSupport.clientParameters().getEndpointIdentificationAlgorithm()).isEqualTo("HTTPS");
    }

    @Test
    void theInternalTrustAlsoKeepsThePublicCas() {
        X509TrustManager both = TlsSupport.trustManager(DIR.resolve("ca.crt"), true);
        X509TrustManager onlyInternal = TlsSupport.trustManager(DIR.resolve("ca.crt"), false);
        assertThat(onlyInternal.getAcceptedIssuers().length).isEqualTo(1);
        assertThat(both.getAcceptedIssuers().length == TlsSupport.systemTrustManager().getAcceptedIssuers().length + 1).isTrue();
        assertThatThrownBy(() -> TlsSupport.readCertificates(DIR.resolve("README.txt"))).isInstanceOf(IllegalStateException.class);
    }
}
