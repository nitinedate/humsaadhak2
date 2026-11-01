package org.humsaadhak.documents.client;

import java.io.InputStream;
import java.time.Duration;

/** Object storage behind an interface so tests and local runs can swap it. */
public interface StorageGateway {
    void put(String key, InputStream content, long size, String contentType);
    String signedDownloadUrl(String key, String downloadName, Duration validFor);
}
