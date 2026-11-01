package org.humsaadhak.documents.client;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import javax.net.ssl.TrustManager;
import org.humsaadhak.shared.security.InternalTls;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Works with AWS S3, DigitalOcean Spaces or MinIO (set humsaadhak.storage.endpoint for the latter two). Every request uses
 * TLS and verifies the storage certificate (public CAs, plus the internal CA for a self-hosted MinIO). Download links are
 * signed for STORAGE_PUBLIC_ENDPOINT when set, so browsers fetch documents over HTTPS from a public address.
 */
@Component
public class S3StorageGateway implements StorageGateway {
    private final S3Client s3;
    private final S3Presigner presigner;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(S3StorageGateway.class);
    private final String bucket;
    private final boolean createBucket;

    public S3StorageGateway(@Value("${humsaadhak.storage.bucket}") String bucket,
                     @Value("${humsaadhak.storage.region:ap-south-1}") String region,
                     @Value("${humsaadhak.storage.endpoint:}") String endpoint,
                     @Value("${humsaadhak.storage.public-endpoint:}") String publicEndpoint,
                     @Value("${humsaadhak.storage.create-bucket:false}") boolean createBucket, InternalTls tls) {
        this.bucket = bucket;
        this.createBucket = createBucket;
        var config = S3Configuration.builder().pathStyleAccessEnabled(!endpoint.isBlank()).build();
        var s3b = S3Client.builder().region(Region.of(region)).credentialsProvider(DefaultCredentialsProvider.builder().build())
                .serviceConfiguration(config)
                .httpClientBuilder(ApacheHttpClient.builder().tlsTrustManagersProvider(() -> new TrustManager[] { tls.trustManager() }));
        var pb = S3Presigner.builder().region(Region.of(region)).credentialsProvider(DefaultCredentialsProvider.builder().build())
                .serviceConfiguration(config);
        if (!endpoint.isBlank()) s3b.endpointOverride(URI.create(endpoint));
        String linkEndpoint = publicEndpoint.isBlank() ? endpoint : publicEndpoint;
        if (!linkEndpoint.isBlank()) pb.endpointOverride(URI.create(linkEndpoint));
        this.s3 = s3b.build();
        this.presigner = pb.build();
    }

    /**
     * A self-hosted MinIO starts empty: with STORAGE_CREATE_BUCKET=true the private documents bucket is created on start-up
     * if it is missing. On AWS the bucket is created with the infrastructure, so this stays off.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket() {
        if (!createBucket) return;
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException missing) {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created storage bucket {}", bucket);
        }
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType)
                .serverSideEncryption("AES256").build(), RequestBody.fromInputStream(content, size));
    }

    @Override
    public String signedDownloadUrl(String key, String downloadName, Duration validFor) {
        GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(key)
                .responseContentDisposition("attachment; filename=\"" + downloadName.replace("\"", "") + "\"").build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(validFor).getObjectRequest(get).build())
                .url().toString();
    }
}
