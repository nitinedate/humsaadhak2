package org.humsaadhak.shared.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * TLS material for encryption in transit between HumSaadhak's services (pure JDK, unit-tested with a real TLS server).
 * Only TLS 1.3 and 1.2 are offered, and clients always verify the server's certificate and hostname.
 */
public final class TlsSupport {
    /** The only protocol versions HumSaadhak speaks. */
    public static final String[] PROTOCOLS = { "TLSv1.3", "TLSv1.2" };

    private TlsSupport() {}

    /** Every certificate in a PEM file. */
    public static List<X509Certificate> readCertificates(Path pem) {
        try (InputStream in = Files.newInputStream(pem)) {
            List<X509Certificate> out = new ArrayList<>();
            for (Certificate c : CertificateFactory.getInstance("X.509").generateCertificates(in)) out.add((X509Certificate) c);
            if (out.isEmpty()) throw new IllegalArgumentException("No certificate in " + pem);
            return out;
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Cannot read certificates from " + pem + ": " + e.getMessage(), e);
        }
    }

    /**
     * Trusts the CA certificates in the PEM file and, when alsoPublicCas is true, the JDK's usual public CAs too (so the
     * same client still reaches Microsoft Graph or AWS). The JDK's own trust manager does the checking.
     */
    public static X509TrustManager trustManager(Path caPem, boolean alsoPublicCas) {
        try {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            int n = 0;
            for (X509Certificate ca : readCertificates(caPem)) store.setCertificateEntry("internal-" + n++, ca);
            if (alsoPublicCas) for (X509Certificate ca : systemTrustManager().getAcceptedIssuers()) store.setCertificateEntry("public-" + n++, ca);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(store);
            return firstX509(tmf);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Cannot build a trust manager from " + caPem + ": " + e.getMessage(), e);
        }
    }

    /** The JDK's default trust manager (public CAs only). */
    public static X509TrustManager systemTrustManager() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            return firstX509(tmf);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Client-side context: verifies servers with the given trust manager. */
    public static SSLContext clientContext(X509TrustManager trust) {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new javax.net.ssl.TrustManager[] { trust }, null);
            return ctx;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Server-side context from a PEM certificate chain and a PKCS#8 private key (as written by deploy/tls/make-certs.sh). */
    public static SSLContext serverContext(Path certPem, Path keyPem) {
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, null);
            List<X509Certificate> chain = readCertificates(certPem);
            store.setKeyEntry("server", privateKey(keyPem), new char[0], chain.toArray(new Certificate[0]));
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(store, new char[0]);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, null);
            return ctx;
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Cannot load server certificate " + certPem + ": " + e.getMessage(), e);
        }
    }

    /** TLS 1.3/1.2 only, and the hostname must match the certificate (HTTPS endpoint identification). */
    public static SSLParameters clientParameters() {
        SSLParameters p = new SSLParameters();
        p.setProtocols(PROTOCOLS.clone());
        p.setEndpointIdentificationAlgorithm("HTTPS");
        return p;
    }

    static PrivateKey privateKey(Path keyPem) throws IOException, GeneralSecurityException {
        String pem = Files.readString(keyPem);
        if (!pem.contains("BEGIN PRIVATE KEY")) throw new IllegalArgumentException("Expected an unencrypted PKCS#8 key (BEGIN PRIVATE KEY) in " + keyPem);
        byte[] der = Base64.getMimeDecoder().decode(pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", ""));
        for (String algorithm : new String[] { "EC", "RSA" }) {
            try { return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(der)); }
            catch (GeneralSecurityException tryNext) { /* not this algorithm */ }
        }
        throw new IllegalArgumentException("Unsupported private key in " + keyPem);
    }

    private static X509TrustManager firstX509(TrustManagerFactory tmf) {
        for (var tm : tmf.getTrustManagers()) if (tm instanceof X509TrustManager x) return x;
        throw new IllegalStateException("No X509TrustManager available");
    }
}
