package org.humsaadhak.documents.entity;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import org.humsaadhak.shared.entity.AuditedEntity;

@Entity
@Table(name = "document")
@Audited
public class Document extends AuditedEntity {
    public enum State { PENDING, REVIEWED, REJECTED }

    @Column(name = "owner_org_id", nullable = false) private long ownerOrgId;
    @Column(name = "document_type_id", nullable = false) private long documentTypeId;
    @Column(name = "need_id") private Long needId;
    @Column(name = "donation_id") private Long donationId;
    @Column(name = "storage_key", nullable = false, unique = true) private String storageKey;
    @Column(name = "original_name", nullable = false) private String originalName;
    @Column(name = "content_type", nullable = false) private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "doc_version", nullable = false) private int docVersion = 1;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private State state = State.PENDING;
    @Column(name = "rejection_reason", length = 2000) private String rejectionReason;
    @Column(nullable = false) private boolean superseded;

    protected Document() {}

    public Document(long ownerOrgId, long typeId, Long needId, Long donationId, String storageKey, String originalName,
             String contentType, long sizeBytes, int docVersion) {
        this.ownerOrgId = ownerOrgId; this.documentTypeId = typeId; this.needId = needId; this.donationId = donationId;
        this.storageKey = storageKey; this.originalName = originalName; this.contentType = contentType;
        this.sizeBytes = sizeBytes; this.docVersion = docVersion;
    }

    public void review() { state = State.REVIEWED; rejectionReason = null; }
    public void reject(String reason) { state = State.REJECTED; rejectionReason = reason; }
    public void supersede() { superseded = true; }

    public long getOwnerOrgId() { return ownerOrgId; }
    public long getDocumentTypeId() { return documentTypeId; }
    public Long getNeedId() { return needId; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalName() { return originalName; }
    public String getContentType() { return contentType; }
    public int getDocVersion() { return docVersion; }
    public State getState() { return state; }
    public String getRejectionReason() { return rejectionReason; }
    public boolean isSuperseded() { return superseded; }
}
