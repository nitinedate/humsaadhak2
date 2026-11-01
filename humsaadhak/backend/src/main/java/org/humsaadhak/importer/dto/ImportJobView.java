package org.humsaadhak.importer.dto;

import org.humsaadhak.importer.model.ImportType;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.*;

public record ImportJobView(long id, ImportType type, String fileName, String status, int rowCount, int createdCount, int errorCount,
               String createdBy, Instant createdAt, List<ImportErrorView> errors) {}
