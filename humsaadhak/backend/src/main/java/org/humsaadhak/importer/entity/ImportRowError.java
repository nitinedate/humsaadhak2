package org.humsaadhak.importer.entity;

import jakarta.persistence.*;
import org.humsaadhak.shared.entity.AuditedEntity;

@Entity
@Table(name = "import_row_error")
public class ImportRowError extends AuditedEntity {
    @Column(name = "job_id", nullable = false) private long jobId;
    @Column(name = "row_number", nullable = false) private int rowNumber;
    @Column(name = "column_name", nullable = false) private String columnName;
    @Column(nullable = false, length = 500) private String message;

    protected ImportRowError() {}
    public ImportRowError(long jobId, int rowNumber, String columnName, String message) {
        this.jobId = jobId; this.rowNumber = rowNumber; this.columnName = columnName; this.message = message;
    }

    // ---- accessors
    public long getJobId() { return jobId; }
    public void setJobId(long jobId) { this.jobId = jobId; }
    public int getRowNumber() { return rowNumber; }
    public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }
    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
