package org.humsaadhak.shared.security;

import java.net.http.HttpClient;
import java.nio.file.Path;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import org.humsaadhak.shared.util.TlsSupport;

/**
 * How HumSaadhak's clients verify the servers they call (search service, file storage): the internal CA from
 * TLS_CA_FILE plus the usual public CAs, or the public CAs alone when no internal CA is configured.
 */
public record InternalTls(SSLContext sslContext, X509TrustManager trustManager, boolean internalCa) {

    public static InternalTls publicCasOnly() {
        X509TrustManager tm = TlsSupport.systemTrustManager();
        return new InternalTls(TlsSupport.clientContext(tm), tm, false);
    }

    public static InternalTls trusting(Path caPem) {
        X509TrustManager tm = TlsSupport.trustManager(caPem, true);
        return new InternalTls(TlsSupport.clientContext(tm), tm, true);
    }

    /** A JDK HTTP client that speaks TLS 1.3/1.2 only and verifies certificate and hostname. */
    public HttpClient.Builder httpClient() {
        return HttpClient.newBuilder().sslContext(sslContext).sslParameters(TlsSupport.clientParameters()).version(HttpClient.Version.HTTP_1_1);
    }
}
