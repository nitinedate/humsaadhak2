package org.humsaadhak.importer.entity;

import org.humsaadhak.importer.model.ImportType;

import jakarta.persistence.*;
import org.humsaadhak.shared.entity.AuditedEntity;

@Entity
@Table(name = "import_job")
public class ImportJob extends AuditedEntity {
    public enum Status { COMPLETED, REJECTED }

    @Enumerated(EnumType.STRING) @Column(nullable = false) private ImportType type;
    @Column(name = "file_name", nullable = false) private String fileName;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "row_count", nullable = false) private int rowCount;
    @Column(name = "created_count", nullable = false) private int createdCount;
    @Column(name = "error_count", nullable = false) private int errorCount;

    protected ImportJob() {}
    public ImportJob(ImportType type, String fileName) { this.type = type; this.fileName = fileName; }

    // ---- accessors
    public ImportType getType() { return type; }
    public void setType(ImportType type) { this.type = type; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public int getRowCount() { return rowCount; }
    public void setRowCount(int rowCount) { this.rowCount = rowCount; }
    public int getCreatedCount() { return createdCount; }
    public void setCreatedCount(int createdCount) { this.createdCount = createdCount; }
    public int getErrorCount() { return errorCount; }
    public void setErrorCount(int errorCount) { this.errorCount = errorCount; }
}
