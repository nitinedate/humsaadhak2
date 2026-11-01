package org.humsaadhak.documents.entity;

import jakarta.persistence.*;
import org.humsaadhak.shared.entity.AuditedEntity;

/** Which document types are mandatory for which organisation type (fixes D7). */
@Entity
@Table(name = "document_type_rule", uniqueConstraints = @UniqueConstraint(columnNames = {"document_type_id", "organisation_type"}))
public class DocumentTypeRule extends AuditedEntity {
    @Column(name = "document_type_id", nullable = false) private long documentTypeId;
    @Column(name = "organisation_type", nullable = false) private String organisationType;
    @Column(nullable = false) private boolean mandatory;

    protected DocumentTypeRule() {}

    public DocumentTypeRule(long documentTypeId, String organisationType, boolean mandatory) {
        this.documentTypeId = documentTypeId; this.organisationType = organisationType; this.mandatory = mandatory;
    }

    public String getOrganisationType() { return organisationType; }
    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }

    public long getDocumentTypeId() { return documentTypeId; }
}
