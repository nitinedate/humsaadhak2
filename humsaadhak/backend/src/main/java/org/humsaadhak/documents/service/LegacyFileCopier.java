package org.humsaadhak.documents.service;

import org.humsaadhak.documents.client.StorageGateway;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.humsaadhak.documents.dao.LegacyDocumentDao;
import org.humsaadhak.documents.dao.LegacyDocumentDao.PendingFile;
import org.springframework.stereotype.Component;

/**
 * Copies the old /uploads files into object storage after V100 has created the document rows.
 * Safe to run again: only rows still at size 0 are copied. Missing files are listed, not fatal.
 */
@Component
@Profile("legacy-migration")
public class LegacyFileCopier implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LegacyFileCopier.class);

    private final LegacyDocumentDao legacy;
    private final StorageGateway storage;
    private final Path root;

    public LegacyFileCopier(LegacyDocumentDao legacy, StorageGateway storage, @Value("${humsaadhak.legacy.upload-dir}") String uploadDir) {
        this.legacy = legacy; this.storage = storage; this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void run(ApplicationArguments args) {
        var pending = legacy.pendingFiles();
        int copied = 0, missing = 0;
        for (PendingFile p : pending) {
            Path file = root.resolve(p.legacyPath()).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                missing++;
                log.warn("Legacy file not found for document {}: {}", p.id(), p.legacyPath());
                continue;
            }
            try (InputStream in = Files.newInputStream(file)) {
                long size = Files.size(file);
                storage.put(p.storageKey(), in, size, p.contentType());
                legacy.markCopied(p.id(), size);
                copied++;
            } catch (IOException e) {
                missing++;
                log.warn("Could not copy legacy file for document {}: {}", p.id(), e.getMessage());
            }
        }
        log.info("Legacy file copy finished: {} copied, {} missing or unreadable, {} checked", copied, missing, pending.size());
    }
}
