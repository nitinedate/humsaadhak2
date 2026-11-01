package org.humsaadhak.documents.entity;

import jakarta.persistence.*;
import org.humsaadhak.shared.entity.AuditedEntity;

@Entity
@Table(name = "document_type")
public class DocumentType extends AuditedEntity {
    public enum Category { REGISTRATION, DONATION, NEED, MEDIA, ACKNOWLEDGEMENT }

    @Column(nullable = false, unique = true) private String code;
    @Column(nullable = false) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Category category;
    /** Shared with a donor when a share request is approved (registration, audited accounts, budgets). */
    @Column(nullable = false) private boolean shareable;
    /** Inactive types stay on existing documents but are no longer offered for upload. */
    @Column(nullable = false) private boolean active = true;

    protected DocumentType() {}

    public DocumentType(String code, String name, Category category, boolean shareable) {
        this.code = code; this.name = name.trim(); this.category = category; this.shareable = shareable;
    }

    public void update(String name, boolean shareable, boolean active) { this.name = name.trim(); this.shareable = shareable; this.active = active; }
    public String getCode() { return code; }
    public boolean isActive() { return active; }

    public Category getCategory() { return category; }
    public boolean isShareable() { return shareable; }
    public String getName() { return name; }
}
